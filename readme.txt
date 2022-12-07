Project Title: BitTorrent like P2P File sharing protocol
***************************************
Authors:
Dhanush Pakanati 
Raghunandhan Venkatraman Vaidyanadhan
Srisharanya Injarapu

Link To Project Demo:https://uflorida-my.sharepoint.com/:v:/g/personal/dpakanati_ufl_edu/EYwSpifE6whPpciHxSik0eEBH3_PZ2ySypsLKuO1hSN5-Q?e=8ueV1v
***************************************
Execution:
The program has the functionality of running both on local host and linux machines. 
To run on windows execute the peerProcess file in any IDEA in order to avoid version 
mismatch of jdk. (We used IntelliK JE-SDK-1.8). If the input argument is not given 
it is hard coded to peer 1003.
To run on linux machines execute the startPeers.java. Sometimes system might show an 
SDK mismatch which has to be dealed before execution.
Manual Commmands: javac -cp path/jsch-0.1.54 *.java; java -cp peerProcess
****************************************
The log file can be found in log_peer_peerID text file in the root folder(CNProject).
Sample Log files of other peers have also been added though they are not saved here 
for comparision.
The shared file can also be found in the same location in peer_PeerID directory.

