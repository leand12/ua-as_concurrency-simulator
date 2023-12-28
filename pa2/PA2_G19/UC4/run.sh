#!/bin/bash

if [ -z "$1" ]
then
  echo "Error: <kafka-home> not supplied"
  echo "Usage: $0 <kafka-home>"
  exit 0
fi

# check whether user had supplied -h or --help . If yes display usage
if [[ ( $@ == "--help") ||  $@ == "-h" ]]
then
	echo "Usage: $0 <kafka-home>"
	exit 0
fi

kafka_home=$1
# remove trailing slash if given
length=${#kafka_home}
last_char=${kafka_home:length-1:1}
[[ $last_char == "/" ]] && kafka_home=${kafka_home:0:length-1}; :

n_brokers=3
n_in_sync=2

# start zookeper
echo "Starting zookeeper..."
$kafka_home/bin/zookeeper-server-start.sh $kafka_home/config/zookeeper.properties > $kafka_home/logs/zookeeper.out 2>&1 &

# start kafka brokers
range=$(($n_brokers-1))
for i in $(eval echo "{0..$range}")
do
    sleep 5
    printf "Starting kafka broker $i...\n"
    $kafka_home/bin/kafka-server-start.sh ./config/server$i.properties > $kafka_home/logs/server$i.out 2>&1 &
done

sleep 5

# create topic
echo 'Creating topic "Sensor"...'
$kafka_home/bin/kafka-topics.sh --create --topic Sensor --partitions 6 --replication-factor $n_brokers --config min.insync.replicas=$n_in_sync --bootstrap-server localhost:9092 2>&1 &
