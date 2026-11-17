package edu.sdu.lab3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TreeSet;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

/** Exactly ten PageRank iterations, including uniform dangling-mass redistribution. */
public final class PageRank {
    private static final int ITERATIONS = 10;
    private static final double DAMPING = 0.85;
    enum GraphCounter { NODES, DANGLING }
    private PageRank() { }

    private static String page(String value) throws IOException {
        String name = value.trim();
        if (name.isEmpty() || name.matches(".*[\\s,()\\t].*")) {
            throw new IOException("Invalid page name: " + value);
        }
        return name;
    }

    /** Emit every source and every destination; reducers union duplicate edges. */
    public static class NormalizeMapper extends Mapper<LongWritable, Text, Text, Text> {
        @Override public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String line = value.toString().replace("\uFEFF", "");
            if (line.trim().isEmpty()) return;
            String[] parts = line.split("\\t", -1);
            if (parts.length != 2) throw new IOException("Expected page<TAB>links: " + line);
            String source = page(parts[0]);
            context.write(new Text(source), new Text("N"));
            if (!parts[1].trim().isEmpty()) {
                for (String link : parts[1].split(",", -1)) {
                    String target = page(link);
                    context.write(new Text(source), new Text("E" + target));
                    context.write(new Text(target), new Text("N"));
                }
            }
        }
    }

    public static class NormalizeReducer extends Reducer<Text, Text, Text, Text> {
        @Override public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            TreeSet<String> links = new TreeSet<>();
            for (Text value : values) {
                String s = value.toString();
                if (s.startsWith("E")) links.add(s.substring(1));
            }
            context.getCounter(GraphCounter.NODES).increment(1);
            if (links.isEmpty()) context.getCounter(GraphCounter.DANGLING).increment(1);
            // The first rank mapper replaces the placeholder with 1/N.
            context.write(key, new Text("0.0\t" + String.join(",", links)));
        }
    }

    /** Preserve graph structure independently of incoming rank contributions. */
    public static class RankMapper extends Mapper<LongWritable, Text, Text, Text> {
        @Override public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String[] fields = value.toString().split("\\t", -1);
            if (fields.length != 3) throw new IOException("Malformed rank state: " + value);
            Configuration conf = context.getConfiguration();
            double rank = conf.getBoolean("pr.first", false)
                    ? 1.0 / conf.getLong("pr.nodes", 1) : Double.parseDouble(fields[1]);
            context.write(new Text(fields[0]), new Text("S\t" + fields[2]));
            if (!fields[2].isEmpty()) {
                String[] links = fields[2].split(",");
                for (String target : links) {
                    context.write(new Text(target), new Text("C\t" + (rank / links.length)));
                }
            }
        }
    }

    public static class RankReducer extends Reducer<Text, Text, Text, Text> {
        private MultipleOutputs<Text, Text> extra;
        @Override public void setup(Context context) { extra = new MultipleOutputs<>(context); }
        @Override public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            String links = null;
            double incoming = 0.0;
            for (Text value : values) {
                String s = value.toString();
                if (s.startsWith("S\t")) links = s.substring(2);
                else incoming += Double.parseDouble(s.substring(2));
            }
            if (links == null) throw new IOException("Missing structure for " + key);
            Configuration conf = context.getConfiguration();
            long n = conf.getLong("pr.nodes", 1);
            double dangling = conf.getDouble("pr.dangling", 0.0);
            double rank = (1.0 - DAMPING) / n + DAMPING * (incoming + dangling / n);
            context.write(key, new Text(Double.toString(rank) + "\t" + links));
            // Only scalar dangling ranks are read by the driver, never all graph records.
            if (links.isEmpty()) extra.write("mass", key, new Text(Double.toString(rank)), "mass/part");
        }
        @Override public void cleanup(Context context) throws IOException, InterruptedException {
            extra.close();
        }
    }

    public static class FormatMapper extends Mapper<LongWritable, Text, Text, NullWritable> {
        @Override public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String[] fields = value.toString().split("\\t", -1);
            context.write(new Text(String.format(Locale.ROOT, "(%s,%.10f)",
                    fields[0], Double.parseDouble(fields[1]))), NullWritable.get());
        }
    }

    private static Job job(Configuration conf, String name, Path input, Path output) throws IOException {
        Job job = Job.getInstance(conf, name);
        job.setJarByClass(PageRank.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);
        return job;
    }

    private static void finish(Job job) throws Exception {
        if (!job.waitForCompletion(true)) throw new IOException("Job failed: " + job.getJobName());
    }

    private static double readDangling(FileSystem fs, Path directory) throws IOException {
        double sum = 0.0;
        FileStatus[] files = fs.globStatus(new Path(directory, "mass/part*"));
        if (files == null) return 0.0;
        for (FileStatus file : files) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    fs.open(file.getPath()), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sum += Double.parseDouble(line.split("\\t")[1]);
            }
        }
        return sum;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: hadoop jar pagerank-mapreduce-1.0.0.jar <input> <new-output-dir>");
            System.exit(2);
        }
        Configuration conf = new Configuration();
        Path output = new Path(args[1]);
        FileSystem fs = output.getFileSystem(conf);
        if (fs.exists(output)) throw new IOException("Output already exists: " + output);
        Path state = new Path(output, "normalized");
        Job normalize = job(conf, "PageRank normalize", new Path(args[0]), state);
        normalize.setMapperClass(NormalizeMapper.class);
        normalize.setReducerClass(NormalizeReducer.class);
        finish(normalize);
        long n = normalize.getCounters().findCounter(GraphCounter.NODES).getValue();
        if (n == 0) throw new IOException("Graph is empty");
        double dangling = (double) normalize.getCounters().findCounter(GraphCounter.DANGLING).getValue() / n;
        conf.setLong("pr.nodes", n);
        System.out.printf(Locale.ROOT, "N=%d; damping=%.2f; iterations=%d%n", n, DAMPING, ITERATIONS);
        for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
            conf.setBoolean("pr.first", iteration == 1);
            conf.setDouble("pr.dangling", dangling);
            Path next = new Path(output, String.format(Locale.ROOT, "iteration-%02d", iteration));
            Job rank = job(conf, "PageRank iteration " + iteration, new Path(state, "part-r-*"), next);
            rank.setMapperClass(RankMapper.class);
            rank.setReducerClass(RankReducer.class);
            MultipleOutputs.addNamedOutput(rank, "mass", TextOutputFormat.class, Text.class, Text.class);
            finish(rank);
            dangling = readDangling(fs, next);
            System.out.printf(Locale.ROOT, "iteration=%d dangling=%.15f%n", iteration, dangling);
            state = next;
        }
        Job format = job(conf, "PageRank final decimal10", new Path(state, "part-r-*"), new Path(output, "result"));
        format.setMapperClass(FormatMapper.class);
        format.setNumReduceTasks(0);
        format.setMapOutputValueClass(NullWritable.class);
        format.setOutputValueClass(NullWritable.class);
        finish(format);
        System.out.println("Result: " + new Path(output, "result"));
    }
}
