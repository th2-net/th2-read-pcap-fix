# read pcap fix (0.0.2)

## Overview

This component is designed to read FIX messages from pcaps and publish them as raw messages.

In case of shutdown, the process can be resumed from the point where it was interrupted.
States, which are stored to a disk after a certain interval, are used for that.
Note: a PersistentVolume (PV) is required for this component to store the component's states.

## Configuration parameters

All values after `:` are default values.

**use_mstore: _true_** — the flag that is responsible for saving messages to 
[mstore](https://github.com/th2-net/th2-mstore) instead of a database

**checkpoint_interval: _10000_** — the number of packets after which the state will be saved

**dead_connections_scan_interval: _100000_** — this parameter specifies the number of processed packets after which the 
scanning of dead connections is run

**connection_death_interval: _10800000_** — this parameter specifies the time (in milliseconds) between two packet
timestamps after which the connection is considered dead

**read_state: _true_** — this parameter specifies if the component reads its state when (re)staring in order to 
resume the processing

**write_state: _true_** — this parameter specifies if the component write its state which is used 
to resume the processing

**use_timestamp_from_pcap: _true_** — this parameter specifies if timestamps of the raw messages will be set 
as they are in PCAPs (true) or as the current time on the server (false)

**sleep_interval: _60_** — number of seconds that the component will wait after finishing processing of all 
available packets in PCAPs before
checking if there's more to be processed

**number_of_packets_to_successful_restore: _10_** — the number of packets needed to successfully restore a gap 
in a stream message stream

**use_event_publish: _true** — this parameter specifies if the component will publish any events

**disable_cradle_saving: _false_** — this parameter specifies if the component will store the messages to 
[cradle](https://github.com/th2-net/cradleapi)

**disable_connectivity_messages: _false_** — this parameter specifies if the connection event will be stored

**event_batch_size: _1048576_** — this parameter specifies the size of an event batch (1MB by default)

**event_batcher_core_pool_size: _100_** —  this parameter specifies the number of events necessary for sending another 
event batch

**event_batcher_max_flush_time: _1000_** — the number of milliseconds for scheduling the sending of event batches

**use_offset_from_cradle: _true_** — this parameter is used for synchronization of PCAP packet sequences with
packet sequences from Cradle

**individual_read_configurations** — this parameter specifies a set of individual configurations for connections

**message_batcher_core_pool_size: _100_** — this parameter specifies the number of events necessary for sending another
message batch

**message_batcher_max_flush_time: _1000_** — the number of milliseconds for scheduling the sending of message batches

**buffered_reader_chunk_size: _8192_** — the buffer size for BufferedInputStream used for reading PCAP files

**check_message_batch_sequence_growth: _true_** — this parameter specifies if the component checks whether 
sequence numbers are incremental in message batches

**check_message_batch_timestamp_growth: _false_** — this parameter specifies if the component checks whether
timestamps are incremental in message batches 

**tcpdump_snapshot_length: _262144_** — this parameter specifies the maximum packet length

**sort_th2_messages: _false_** — this parameter specifies if messages should be sorted by timestamps

**usable_fraction_of_batch_size: _0.9_** — the fraction of batch size to be used; can be from 0 to 1

**message_sorter_window_size: _15000_** — the time window size in which a sorting is performed

**message_sorter_connection_end_timeout: _30000_** — this parameter specifies the amount of time (in milliseconds)
that is supposed to pass between two adjacent packets so the connection would be considered stale and then deleted

**message_sorter_clear_interval: _2000_** — the amount of messages used by MessageSorter to start searching for
stale sessions (i.e. after sorting the next 2000 messages the search begins)

**possible_time_window_between_files: _1000_** — this parameter specifies the **maximum** amount of time (in milliseconds)
that is supposed to pass between two adjacent PCAPs 
(the last packet timestamp for 1st PCAP and the first packet timestamp from the 2nd PCAP are compared).

**check_message_size_exceeds_batch_size: _true_** — this parameter specifies if a message size exceeding a batch size 
should be checked of not


## Individual configuration parameters

**pcap_directory** — a directory with pcap files

**pcap_file_prefix** — a prefix of the pcap files

**read_tar_gz_archives** — this parameter specifies if tar.gz archives should be processed

**tar_gz_archive_prefix** — a prefix of the tar.gz pcap files

**connection_addresses** — a list of connection addresses; an example of one is in the next section

**state_file_path** — a path used by the state to resume processing

## Connection address

**ip** — an IP address
**port** — a port
**protocol** — a protocol of packets from a pcap
**stream_name** — the name that is used for message storing

## Example of infra-schema

```yaml
checkpoint_interval: 500000
dead_connections_scan_interval: 100000

read_state: false
write_state: true
use_timestamp_from_pcap: true
disable_connectivity_messages: true
use_event_publish: true
use_mstore: true
use_offset_from_cradle: false
check_message_size_exceeds_batch_size: true

individual_read_configurations:
  - pcap_directory: "/var/tmp/pcaps/prod_pcaps/"
    pcap_file_prefix: ''
    state_file_path: "/var/tmp/pcaps/state_dir/"
    connection_addresses:
      - IP: "111.2.3333.44"
        port: "7000"
        protocol: "FIX"
        stream_name: "stream_1"

      - IP: "555.6.777.88"
        port: "7001"
        protocol: "FIX"
        stream_name: "stream_2"
```