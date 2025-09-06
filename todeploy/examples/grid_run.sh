#!/bin/bash
# ---------------------------------------------------------------------------------------------
# This bash script runs the Grid example which uses 3 nodes.
# ---------------------------------------------------------------------------------------------

cd ..

#x-terminal-emulator -e "java -disableassertions \
#                             -javaagent:lib/lang.jar \
#                             -jar stick.jar examples/67.grid_node1.model \
#                                            -config=examples/grid_config_node1.json" &

x-terminal-emulator -e "java -disableassertions \
                             -javaagent:lib/lang.jar \
                             -jar stick.jar examples/68.grid_node2.model \
                                            -config=examples/grid_config_node2.json" #&


#x-terminal-emulator -e "java -disableassertions \
#                             -javaagent:lib/lang.jar \
#                             -jar stick.jar examples/69.grid_node3.model \
#                                            -config=examples/grid_config_node3.json" &