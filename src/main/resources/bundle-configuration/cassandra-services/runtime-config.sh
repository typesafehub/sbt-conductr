# Setting Cassandra configuration directory to the bundle configuration directory
BUNDLE_CONFIG_DIR="$( cd $( dirname "${BASH_SOURCE[0]}" ) && pwd )"
export CASSANDRA_CONF=$BUNDLE_CONFIG_DIR/cassandra-conf