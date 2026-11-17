#!/usr/bin/env python3
"""Write isolated, small-memory pseudo-distributed lab configurations."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(os.environ.get('LAB_RUNTIME', '/tmp/codex-pagerank-runtime')).resolve()
def config(path, values):
    tree = ET.Element('configuration')
    for key, value in values.items():
        prop = ET.SubElement(tree, 'property')
        ET.SubElement(prop, 'name').text = key
        ET.SubElement(prop, 'value').text = str(value)
    ET.indent(tree)
    Path(path).write_text(ET.tostring(tree, encoding='unicode'), encoding='utf-8')

hd = root / 'hadoop-3.3.6/etc/hadoop'
for name in ['hadoop','hbase','spark']:
    for kind in ['logs','pids','data']:
        (root/kind/name).mkdir(parents=True, exist_ok=True)
config(hd/'core-site.xml', {'fs.defaultFS':'hdfs://127.0.0.1:19000','hadoop.tmp.dir':root/'data/hadoop/tmp'})
config(hd/'hdfs-site.xml', {
    'dfs.replication':1, 'dfs.namenode.name.dir':root/'data/hadoop/name',
    'dfs.datanode.data.dir':root/'data/hadoop/data', 'dfs.namenode.http-address':'127.0.0.1:19870',
    'dfs.datanode.address':'127.0.0.1:19866', 'dfs.datanode.http.address':'127.0.0.1:19864',
    'dfs.datanode.ipc.address':'127.0.0.1:19867', 'dfs.permissions.enabled':'false'})
config(hd/'yarn-site.xml', {
    'yarn.resourcemanager.hostname':'localhost', 'yarn.resourcemanager.address':'127.0.0.1:18032',
    'yarn.resourcemanager.scheduler.address':'127.0.0.1:18030',
    'yarn.resourcemanager.resource-tracker.address':'127.0.0.1:18031',
    'yarn.resourcemanager.admin.address':'127.0.0.1:18033',
    'yarn.resourcemanager.webapp.address':'127.0.0.1:18088',
    'yarn.nodemanager.hostname':'localhost', 'yarn.nodemanager.address':'127.0.0.1:18040',
    'yarn.nodemanager.webapp.address':'127.0.0.1:18042',
    'yarn.nodemanager.localizer.address':'127.0.0.1:18044',
    'yarn.nodemanager.aux-services':'mapreduce_shuffle', 'mapreduce.shuffle.port':13562,
    'yarn.nodemanager.resource.memory-mb':3072, 'yarn.nodemanager.resource.cpu-vcores':4,
    'yarn.scheduler.minimum-allocation-mb':256, 'yarn.scheduler.maximum-allocation-mb':3072,
    'yarn.nodemanager.vmem-check-enabled':'false', 'yarn.nodemanager.pmem-check-enabled':'false',
    'yarn.nodemanager.env-whitelist':'JAVA_HOME,HADOOP_COMMON_HOME,HADOOP_HDFS_HOME,HADOOP_CONF_DIR,CLASSPATH_PREPEND_DISTCACHE,HADOOP_YARN_HOME,HADOOP_MAPRED_HOME,PATH,LANG,TZ,JAVA_TOOL_OPTIONS',
    'yarn.nodemanager.local-dirs':root/'data/hadoop/nm-local',
    'yarn.nodemanager.log-dirs':root/'logs/hadoop/containers'})
config(hd/'mapred-site.xml', {
    'mapreduce.framework.name':'yarn', 'mapreduce.application.classpath':str(root/'hadoop-3.3.6/share/hadoop/mapreduce/*')+','+str(root/'hadoop-3.3.6/share/hadoop/mapreduce/lib/*'),
    'yarn.app.mapreduce.am.env':f'HADOOP_MAPRED_HOME={root}/hadoop-3.3.6',
    'mapreduce.map.env':f'HADOOP_MAPRED_HOME={root}/hadoop-3.3.6',
    'mapreduce.reduce.env':f'HADOOP_MAPRED_HOME={root}/hadoop-3.3.6',
    'yarn.app.mapreduce.am.resource.mb':512, 'yarn.app.mapreduce.am.command-opts':'-Xmx384m -XX:ActiveProcessorCount=2',
    'mapreduce.map.memory.mb':256, 'mapreduce.map.java.opts':'-Xmx192m -XX:ActiveProcessorCount=2',
    'mapreduce.reduce.memory.mb':256, 'mapreduce.reduce.java.opts':'-Xmx192m -XX:ActiveProcessorCount=2',
    'mapreduce.job.reduce.slowstart.completedmaps':1.0,
    'mapreduce.jobhistory.address':'127.0.0.1:11020',
    'mapreduce.jobhistory.webapp.address':'127.0.0.1:19888'})
(hd/'hadoop-env.sh').write_text(f'export JAVA_HOME="{root}/zulu11.84.17-ca-jdk11.0.29-linux_x64"\nexport HADOOP_HEAPSIZE_MAX=256\nexport HADOOP_OPTS="-XX:ActiveProcessorCount=4"\n',encoding='utf-8')
hb=root/'hbase-2.5.11/conf'
config(hb/'hbase-site.xml', {'hbase.rootdir':f'file://{root}/data/hbase/root', 'hbase.cluster.distributed':'false', 'hbase.zookeeper.property.clientPort':12181, 'hbase.zookeeper.property.dataDir':root/'data/hbase/zookeeper', 'hbase.master.port':16000, 'hbase.master.info.port':16010, 'hbase.regionserver.port':16020, 'hbase.regionserver.info.port':16030, 'hbase.unsafe.stream.capability.enforce':'false'})
(hb/'hbase-env.sh').write_text(f'export JAVA_HOME="{root}/zulu11.84.17-ca-jdk11.0.29-linux_x64"\nexport HBASE_HEAPSIZE=512\nexport HBASE_OPTS="-XX:ActiveProcessorCount=4"\nexport HBASE_MANAGES_ZK=true\n',encoding='utf-8')
print('Configured isolated runtime:',root)
