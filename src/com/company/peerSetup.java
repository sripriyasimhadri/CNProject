package com.company;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.net.*;
import java.util.concurrent.*;

//_______________________________________________________________________________________________________________________________________________________________

//sett download rates
public class peerSetup {
    static com.company.Node pInstance;
    static String id_peer;
    public static void main(String[] in) throws Exception {
        id_peer = "1002";
        if(in.length != 0)
            id_peer = in[0];
        pInstance = new com.company.Node(id_peer, new Configuration_Setter());
        pInstance.retrieve_Config();
        pInstance.retrieve_Peer_Details();
        selectionOfPec();
        if(pInstance.initFileExistsG_t())
            SplitF();
        com.company.ServerInformation serverInstance = new com.company.ServerInformation(pInstance.getPortNumber(), pInstance);
        pInstance.Srv_Setter(serverInstance);
        serverInstance.start();

        for(com.company.peerInformation informationSvr : pInstance.retrieve_P2Conn()) {
            Socket socket_request = null;
            try {
                socket_request = new Socket(informationSvr.getNodeName(), informationSvr.getPortNumber());
                //connecting to socket with socke class
                ObjectOutputStream so = new ObjectOutputStream(socket_request.getOutputStream());
                //output stream to socket
                //log connection
                System.out.println("Established connection with peer -  " + informationSvr.getNodeName() + " at port number:" + " " + informationSvr.getPortNumber());
                //input stream to socket
                ObjectInputStream result = new ObjectInputStream(socket_request.getInputStream());
                //create handshake msg to be sent
                com.company.HandShake hs_obj = new com.company.HandShake(pInstance.getNeighbourBit());
                //write handshake to the object
                so.writeObject(hs_obj );
                //log connection
                pInstance.log_Writer("At Time: ["+ Instant.now() + "]: id_peer: [" + id_peer +"] connected to id_peer: [" + informationSvr.getNeighbourBit()+"]");
                System.out.println("Handshake message sent from "+ id_peer+" to "+ informationSvr.getNeighbourBit());
                //create hashmap to update status
                Map<String, String> hs_status = pInstance.retrieveHSVal();
                hs_status.put(informationSvr.getNeighbourBit(), "Sent"); //updating
                pInstance.setHSVal(hs_status);
                //object to ConnectionDetails
                com.company.PropertiesOfConnection connection_info = new com.company.PropertiesOfConnection(informationSvr.getNeighbourBit(), socket_request , so, result, pInstance, new ConcurrentLinkedQueue<Object>());
                //Add them to peer
                pInstance.retrieve_Conn().add(connection_info);
                pInstance. retrieveP2PCon().put(informationSvr.getNeighbourBit(), connection_info);
                connection_info.start();
            } catch (IOException c) {

                System.out.println("Exception In socket establishment and handshake: "+c);
            }
        }
        checkTime();
        System.out.println("Done");
        System.exit(0);
    }

    public static void checkTime() throws InterruptedException {
        Thread time_check = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Thread: " + Thread.currentThread().getName());
                final ScheduledExecutorService pns = Executors.newScheduledThreadPool(1);
                final ScheduledExecutorService ons = Executors.newScheduledThreadPool(1);
                Runnable prefNum = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            chockMSG();
                            NeighMSGG();
                            mUChok();
                            StringBuilder builder = new StringBuilder("At time: ["+ Instant.now() + "]: the list of preferred neighbours of PeerID [" + id_peer +"] is [");
                            for(String s : pInstance. retrievePrefNeighbours()) {
                                builder.append(s + ", ");
                            }
                            builder.append("]");
                            pInstance.log_Writer(builder.toString());
                        } catch (IOException e) {
                            System.out.println("Exception in Preferred Neighbours List: " +e);
                        }
                    }
                };
                Runnable selectNR = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            retrieveBNum();
                            StringBuilder builder = new StringBuilder("At time ["+ Instant.now() + "]: list of Optimistic unchoked neighbors for PeerID [" + id_peer +"] is [");
                            for(String s : pInstance.BestNbrg_t()) {
                                builder.append(s + ", ");
                            }
                            builder.append("]");
                            pInstance.log_Writer(builder.toString());
                        } catch (IOException c) {
                            System.out.println("Exception in Optimistic Unchoked Neighbours: "+c);
                        }
                    }
                };
                final ScheduledFuture<?> PrefNSched = pns.scheduleAtFixedRate(prefNum, 0, Configuration_Setter.unChokingInt, TimeUnit.SECONDS);
                final ScheduledFuture<?> OptNES = ons.scheduleAtFixedRate(selectNR, 1, Configuration_Setter.opUnchkInt, TimeUnit.SECONDS);

                while(true) {
                    try {
                        Thread.sleep(3000);
                        if(finishedBoolens()) {
                            System.out.println("Stop timer");
                            //ended here
                            PrefNSched.cancel(true);
                            OptNES.cancel(true);
                            break;
                        }
                    } catch (InterruptedException c) {
                        System.out.println("Timer Exception:" +c);
                    } catch (IOException c) {
                        System.out.println("General Exception:" +c);
                    }
                }
            }
        });

        while(pInstance.retrieve_Interested_Key().size() == 0) {
            //wait 1000ms
            Thread.sleep(1000);
        }
        time_check.start();
        while(time_check.isAlive()) {
            //wait 1000ms
            Thread.sleep(1000);
        }
    }

    public static void chockMSG() throws IOException {
        //Determining new set of neighbours to choke from previous list of preferred neighbours
        Set<String> nonChocked = pInstance.nonChkdKeySetter();
        Set<String> Chok = pInstance.retrieveChokedKey();
        Map<String, com.company.PropertiesOfConnection> peerC = pInstance. retrieveP2PCon();
        Set<String> prefNum = pInstance. retrievePrefNeighbours();

        for(String id_peer : prefNum) {
            com.company.PropertiesOfConnection connection_info = peerC.get(id_peer);
            connection_info.sendMessage(new com.company.Realistic(0, null));
            //Updating Choked list
            Chok.add(id_peer);
            //Updating Unchoked list
            nonChocked.remove(id_peer);
        }

        prefNum.clear();
        pInstance.prefNeighbourSetter(prefNum);
        pInstance.nonChkKeySetter(nonChocked);
        pInstance.chokeKeySetter(Chok);
    }

    public static void NeighMSGG() throws IOException {
        Set<String> Chok = pInstance.retrieveChokedKey();
        Set<String> nonChocked = pInstance.nonChkdKeySetter();
        Set<String> agreed = pInstance.retrieve_Interested_Key();
        Set<String> prefNum = new HashSet<>();
        ConcurrentMap<String, Integer> speedD = pInstance.retrieveDownloadRt();
        PriorityQueue<com.company.RateODown> prQueue = new PriorityQueue<com.company.RateODown>((a, b) -> b.speedD - a.speedD);

        for(String sink : speedD.keySet()) {
            prQueue.add(new com.company.RateODown(sink, speedD.get(sink)));

            speedD.put(sink, 0);
        }

        int pNN = Configuration_Setter.numOfPrefNeigh;
        while(pNN > 0 && prQueue.size() != 0) {
            com.company.RateODown node = prQueue.poll();
           /*Unchoking k preferred neighbours based on Download rate during the previous
            unchoking interval*/
            if(agreed.contains(node.IDOfP)) {
                //Updating list of Choked, Unchoked and preferred neighbours
                nonChocked.add(node.IDOfP);
                Chok.remove(node.IDOfP);
                prefNum.add(node.IDOfP);
                //Decrement till k preferred neighbours are reached
                pNN--;
            }
        }

        pInstance.prefNeighbourSetter(prefNum);
        pInstance.nonChkKeySetter(nonChocked);
        pInstance.chokeKeySetter(Chok);
        pInstance.interestedKeySetter(agreed);
        pInstance.downloadRSetter(speedD);
    }

    public static void mUChok() throws IOException {
        Map<String, com.company.PropertiesOfConnection> peerC = pInstance. retrieveP2PCon();

        for(String IDOfP : pInstance.retrievePrefNeighbours()) {
            com.company.PropertiesOfConnection connection_info = peerC.get(IDOfP);
            connection_info.sendMessage(new com.company.Realistic(1, null));
        }

    }

    public static void retrieveBNum() throws IOException {

        Set<String> selectNR = pInstance.BestNbrg_t();
        Set<String> agreed = pInstance.retrieve_Interested_Key();
        Set<String> Chok = pInstance.retrieveChokedKey();
        Set<String> nonChocked = pInstance.nonChkdKeySetter();
        List<String> iAndCoc = new ArrayList<>();
        Map<String, com.company.PropertiesOfConnection> peerC = pInstance.retrieveP2PCon();

        for(String peer_ID : selectNR) {
            com.company.PropertiesOfConnection connection_info = peerC.get(peer_ID);
            connection_info.sendMessage(new com.company.Realistic(0, null));
            selectNR.remove(peer_ID);
            Chok.add(peer_ID);
        }

        for(String peer_ID : agreed) {
            if(Chok.contains(peer_ID)) {
                iAndCoc.add(peer_ID);
            }
        }

        Collections.shuffle(iAndCoc);

        if(iAndCoc.size() != 0) {
            String peer_ID = iAndCoc.get(0);
            com.company.PropertiesOfConnection connection_info = peerC.get(peer_ID);
            connection_info.sendMessage(new com.company.Realistic(1, null));
            nonChocked.add(peer_ID);
            Chok.remove(peer_ID);
            selectNR.add(peer_ID);
        }

        pInstance.chokeKeySetter(Chok);
        pInstance.nonChkKeySetter(nonChocked);
        pInstance.interestedKeySetter(agreed);
        pInstance.BestNbrs_t(selectNR);
    }

    public static void SplitF() throws IOException {
        // Splitting the file into prices based on File and piece size
        // Splitting the file into prices based on File and piece size
        File file = new File("/Users/simhadrisripriya/Downloads/CNProject 3/src/com/company/thefile");
        FileInputStream inputSm = new FileInputStream(file);
        BufferedInputStream bufSrm = new BufferedInputStream(inputSm);

        byte[] objs;
        long FSlen = file.length();
        long pre = 0;
        int index = 0;

        ConcurrentMap<Integer, byte[]> indexOfEle = new ConcurrentHashMap<>();

        while(pre < FSlen){
            int wid = Configuration_Setter.sizeOfPiece;
            if(FSlen - pre >= wid)
                pre += wid;
            else{
                wid = (int)(FSlen - pre);
                pre = FSlen;
            }
            objs = new byte[wid];
            bufSrm.read(objs, 0, wid);
            indexOfEle.put(index, objs);
            index++;
        }

        pInstance.setPieceAtIndex(indexOfEle);
    }

    public static void mrgAFiles() throws IOException {
        Map<Integer, byte[]> indexOfEle = pInstance.getPieceAtIndex();
        int bits = Configuration_Setter.pieces_total;
        FileOutputStream ouSt = new FileOutputStream(Configuration_Setter.nameOfFile);
        for(int z = 0; z < bits; z++) {
            ouSt.write(indexOfEle.get(z));
        }
    }

    public static void selectionOfPec() {

        int IDOfP = Integer.parseInt(pInstance.getNeighbourBit());
        int numOfP = pInstance.retrieve_Peers().size();
        int totalRange = (int) Math.ceil(Configuration_Setter.pieces_total / numOfP);
        int small = Math.max(0, (IDOfP % 1000 - 1) * totalRange);
        int big = Math.min(small + totalRange, Configuration_Setter.pieces_total);

        PriorityBlockingQueue<com.company.select_a_node> prQueue = new PriorityBlockingQueue<com.company.select_a_node>(10, (z, y) -> z.point_y - y.point_y);

        List<com.company.select_a_node> line = new ArrayList<>();

        for(int a = 0; a < Configuration_Setter.pieces_total; a++) {
            if(a >= small && a <= big) {
                line.add(new com.company.select_a_node(2, a));
            }
            else {
                line.add(new com.company.select_a_node(1, a));
            }
        }

        Collections.shuffle(line);

        for(int z = 0; z < line.size(); z++) {
            prQueue.add(line.get(z));
        }

        pInstance.Selects_t(prQueue);

    }

    public static boolean finishedBoolens() throws InterruptedException, IOException {
        List<com.company.peerInformation> allPeers = pInstance.retrieve_Peers();
        int M = 0;
        for(com.company.peerInformation Peer1 : allPeers) {
            String bitField;
            if(Peer1.getNeighbourBit().compareTo(id_peer) == 0) {
                bitField = String.valueOf(pInstance.retrieve_BF());
            }
            else {
                bitField = pInstance.retrieve_BM_Val().getOrDefault(Peer1.getNeighbourBit(), null);
            }
            if(bitField == null)
                continue;
            for(int z = 0; z < bitField.length(); z++) {
                if(bitField.charAt(z) == '0')
                    return false;
            }
            //Increment Counter for each peer that has received all pieces
            M++;
        }
        if(M == 3) {
            //Close all threads when all peers have received all the pieces
            Thread.sleep(10000);
            closeEm();
            contrFS();
            return true;
        }
        return false;
    }

    public static void contrFS() throws IOException {
        File neighborDocs = new File("./peer_" + id_peer);
        if (!neighborDocs.exists()){
            neighborDocs.mkdirs();
        }
        ConcurrentMap<Integer, byte[]> sections = pInstance.getPieceAtIndex();
        File fls = new File("./peer_" + id_peer + "/" + "thefile");
        FileOutputStream resultstream = new FileOutputStream(fls);
        for(int z = 0; z < Configuration_Setter.pieces_total; z++) {
            resultstream.write(sections.get(z));
        }
        resultstream.close();
    }

    public static void closeEm() throws IOException, InterruptedException {
        //Process to end all threads
        ConcurrentMap<String, com.company.PropertiesOfConnection> connts = pInstance.retrieveP2PCon();

        for(String peer : connts.keySet()) {
            com.company.PropertiesOfConnection conn2 = connts.get(pInstance);
            Thread th1 = (Thread) conn2;
            Thread th2 = (Thread) conn2.retrieveHMessage();
            Thread unChoke = (Thread) conn2.retrieveHMessage().uncokThread();
            if(unChoke != null) {
                System.out.println("Peer: "+pInstance+" unchoke thread "+unChoke.getName()+" closed");
                unChoke.stop();
            }
            Thread.sleep(1000);
            if(th2 != null) {
                System.out.println("Peer: "+pInstance+" Message handler thread"+th2.getName()+" closed");
                th2.stop();
            }

            if(th1 != null) {
                System.out.println("Peer: "+pInstance+" Connection thread "+th1.getName()+" closed");
                th1.stop();
            }
        }
        Thread serverInstance = (Thread) pInstance.retrieve_Srv();
        System.out.println("Peer: "+pInstance+" Server thread"+serverInstance.getName()+" closed");
        //Stopping Server thread
        serverInstance.stop();
    }
}
class RateODown {
    //Storing piece Download rates of peers
    String IDOfP;
    Integer speedD;
    public RateODown(String IDOfP, Integer speedD) {
        this.IDOfP = IDOfP;
        this.speedD = speedD;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

//choose the nodes
class select_a_node {
    int point_y;
    int x_identifier;

    public select_a_node(int point_y, int x_identifier) {
        this.point_y = point_y;
        this.x_identifier = x_identifier;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

//Config file update
class Configuration_Setter {
    //declare all the variables from common.cfg
    static int numOfPrefNeigh;
    //unchecking interval in log files
    static int unChokingInt;
    //also log file
    static int opUnchkInt;
    //check with small peers first
    static String nameOfFile;
    static int pieces_total;
    static int sizeOfFile;
    static int sizeOfPiece;

    public Configuration_Setter() {
        try {
            InputStream is = new FileInputStream("/Users/simhadrisripriya/Downloads/CNProject 3/src/com/company/common.cfg");
            Properties prop = new Properties();
            prop.load(is);
            numOfPrefNeigh  = Integer.parseInt(prop.getProperty("numOfPrefNeigh"));
            unChokingInt           = Integer.parseInt(prop.getProperty("unChokingInt"));
            opUnchkInt = Integer.parseInt(prop.getProperty("opUnchkInt"));
            nameOfFile                    = prop.getProperty("nameOfFile");
            sizeOfFile                    = Integer.parseInt(prop.getProperty("sizeOfFile"));
            sizeOfPiece                   = Integer.parseInt(prop.getProperty("sizeOfPiece"));

            if(sizeOfFile%sizeOfPiece == 0){
                pieces_total = sizeOfFile/sizeOfPiece;
            } else {
                pieces_total = (sizeOfFile/sizeOfPiece) + 1;
            }
        } catch (Exception e) {
            System.out.println("Exception in config class: "+e);
        }
    }


}

//_______________________________________________________________________________________________________________________________________________________________

//get and update peer data
class peerInformation {
    private int isfilepresent;

    private String peerIdentifier;

    private String nodeName;

    private int portNumber;


    public peerInformation(String peerIdentifier, String nodeName, int portNumber, int isfilepresent)
    {
        this.peerIdentifier = peerIdentifier;
        this.nodeName = nodeName;
        this.portNumber = portNumber;
        this.isfilepresent = isfilepresent;
    }
    public String getNeighbourBit()
    {
        return peerIdentifier;
    }
    public void threadIDSetter(String peerIdentifier)
    {
        this.peerIdentifier = peerIdentifier;
    }
    public String getNodeName()
    {
        return nodeName;
    }
    public void setNodeName(String nodeName)
    {
        this.nodeName = nodeName;
    }
    public int getPortNumber()
    {
        return portNumber;
    }
    public void set_PortNumber(int portNumber)
    {
        this.portNumber = portNumber;
    }
    public int fileext_gt()
    {
        return isfilepresent;
    }
    public void fileext_st(int isfilepresent)
    {
        this.isfilepresent = isfilepresent;
    }
}

class PropertiesOfConnection extends Thread {

    private String processID;
    private Socket socNumber;
    private ObjectOutputStream outputStreamVar;
    private ObjectInputStream inputStreamVar;
    private boolean handshakeImplemeted;
    protected Queue<Object> queuedMessage;
    private Peer localPeerData;
    private MsgProcessor messageCtlr;
    public MsgProcessor retrieveHMessage() {
        return messageCtlr;
    }
    public Queue<Object> getQueuedMsg() {
        return queuedMessage;
    }
    public void SetMessageQueue(Queue<Object> queuedMessage) {
        this.queuedMessage = queuedMessage;
    }
    public int getLength() {
        return queuedMessage.size();
    }
    public PropertiesOfConnection(String processID, Socket socNumber, ObjectOutputStream outputStreamVar, ObjectInputStream inputStreamVar, Peer localPeerData, Queue<Object> queuedMessage) {
        this.processID = processID;
        this.socNumber = socNumber;
        this.outputStreamVar = outputStreamVar;
        this.inputStreamVar = inputStreamVar;
        this.localPeerData = localPeerData;
        this.queuedMessage = queuedMessage;

    }
    public String getNeighbourBit() {
        return processID;
    }
    public void setPeerIdentifier(String processID) {
        this.processID = processID;
    }
    public Socket getSocNumber() {
        return socNumber;
    }
    public void setSocNumber(Socket socNumber) {
        this.socNumber = socNumber;
    }
    synchronized public ObjectOutputStream getOutputStreamVar() {
        return outputStreamVar;
    }
    public void setOutputStreamVar(ObjectOutputStream ououtputStreamVart) {
        this.outputStreamVar = outputStreamVar;
    }
    public ObjectInputStream getIn() {
        return inputStreamVar;
    }
    public void setIn(ObjectInputStream inputStreamVar) {
        this.inputStreamVar = inputStreamVar;
    }
    public boolean handshakeImplemeted() {
        return handshakeImplemeted;
    }
    public void setHandShakeDone(boolean handShakeDone) {
        this.handshakeImplemeted = handShakeDone;
    }
    public void start() {
        try
        {
            System.out.println("In present connection with name:" + Thread.currentThread().getName() + "\nhashcode of the thread:" + this.hashCode() + "\nhashed message in the queue:" + queuedMessage.hashCode());
            this.messageCtlr = new MsgProcessor(this.localPeerData, this, this.queuedMessage);
            messageCtlr.start();
            while (true)

            {
                Object msg_new = this.inputStreamVar.readObject();
                queuedMessage.add(msg_new);
            }
        }
        catch (ClassNotFoundException clsNF)
        {
            System.out.println("Encountered a Class Not Found Exception"+clsNF);
        }
        catch (IOException io1)
        {
            try
            {
                localPeerData.getBw().close();
            }
            catch (IOException io2)
            {
                System.out.println("Encountered an IO exception at buffer reader"+io2);
            }
            System.out.println("Encountered an IO exception:"+io1);
        }
    }

    synchronized public void sendMessage(Object msg_new) throws IOException {
        System.out.print("Response data stream is ");
        if (msg_new instanceof HandShake) {
            System.out.println("A handshake message has been sent to following process ID " + processID);
        } else if (msg_new instanceof Realistic) {
            if (((Realistic) msg_new).messageTypeGet() == 7)
                System.out.println("Message has been sent " + ((Realistic) msg_new).messagePayloadGet().split("-")[0] + " which is of type " + ((Realistic) msg_new).messageTypeGet());
            else
                System.out.println("Message has been sent " + ((Realistic) msg_new).messagePayloadGet() + " which is of type " + ((Realistic) msg_new).messageTypeGet());
        }
        outputStreamVar.writeObject(msg_new);
        outputStreamVar.flush();
    }
}


//_______________________________________________________________________________________________________________________________________________________________

class PieceMessage extends Realistic {
    int messageType;
    byte[] piece;

    public PieceMessage(int messageType, byte[] piece) {
        this.messageType = messageType;
        this.piece = piece;
    }

    public byte[] getPiece() {
        return piece;
    }

    public void setPiece(byte[] piece) {
        this.piece = piece;
    }
}

//_______________________________________________________________________________________________________________________________________________________________


//Handshake implementation
class HandShake implements Serializable{
    private String header = "P2PFILESHARINGPROJ";
    private byte[] z_b = new byte[10];
    private String id_peer;
    private static final long serialVersionUID = 42L;

    public HandShake() {}

    public HandShake(String id_peer) {
        this.id_peer = id_peer;
    }

    public String getHeader() {
        return header;
    }

    public void hdr_setter(String header) {
        this.header = header;
    }

    public byte[] retrieve_z_b() {
        return z_b;
    }

    public void z_b_setter(byte[] z_b) {
        this.z_b = z_b;
    }

    public String getNeighbourBit() {
        return id_peer;
    }

    public void threadIDSetter(String id_peer) {
        this.id_peer = id_peer;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class MsgProcessor extends Thread{
    Peer peer;
    PropertiesOfConnection connection_info;
    Queue<Object> msgQ;
    LinkedList<Integer> pieces = new LinkedList<>();
    Thread unChokeThread;
    int lReq;

    public Thread uncokThread() {
        return unChokeThread;

    }

    public MsgProcessor(Peer peer, PropertiesOfConnection connection_info, Queue<Object> msgQ) {

        this.peer = peer;
        this.connection_info = connection_info;
        this.msgQ = msgQ;
    }


    public void run() {
        System.out.println("Message handler thread started ");

        while(true) {
            while(msgQ.size() != 0) {
                Object message = msgQ.poll();
                System.out.print("[Request ] : ");
                if(message instanceof HandShake) {
                    //instance found imply handshake msg
                    System.out.println("Received handshake message from " + ((HandShake) message).getNeighbourBit()+" Message:" + message);
                    try {
                        peer.log_Writer("At time: [" + Instant.now()+"]: Peer [" + peer.getNeighbourBit()+"] connected after handshake to Peer [" + ((HandShake) message).getNeighbourBit() + "]");
                        HSprocessor((HandShake) message);
                    } catch (IOException e) {
                        System.out.println("Exception in hanshake processing : "+e);

                    }
                }
                else if (message instanceof  PieceMessage) {
                    PieceMessage pieceMessage = (PieceMessage) message;
                    //If message is a piece message, update log file
                    if(pieceMessage.messageTypeGet() == 7)
                        System.out.println("At time: ["+Instant.now().toString()+"] Message type - " + pieceMessage.messageTypeGet() + " received from "+connection_info.getNeighbourBit() +" Msg: " + lReq );
                    try {
                        peer.log_Writer("At time: ["+ Instant.now() + "]: Peer - " + peer.getNeighbourBit() +"  downloaded  piece -" + lReq + " from - " + connection_info.getNeighbourBit() +". Tot Pieces is "+(peer.getPieceAtIndex().size() + 1));
                        // handle Piece Message
                        pieceMessageControl(pieceMessage);
                    } catch (IOException e) {
                        System.out.println("Exception in writing log: "+e);
                    }
                    break;
                }
                else if (message instanceof Realistic){
                    Realistic actMsg = (Realistic) message;
                    if(actMsg.messageTypeGet() == 7)
                        System.out.println(" Message type- " + actMsg.messageTypeGet() + " is recieved from "+connection_info.getNeighbourBit() +"; Msg: " + lReq + " " + Instant.now()
                                .toString());
                    else
                        System.out.println("Message of type " + actMsg.messageTypeGet() + " is received from "+connection_info.getNeighbourBit() +"; Msg: " + actMsg.messagePayloadGet() + Instant.now()
                                .toString());
                    if(actMsg.messageTypeGet() == 0 ){
                        //Checking if message type is choke, then update log and handle
                        try {
                            peer.log_Writer("["+ Instant.now() + "]: Peer [" + peer.getNeighbourBit() +"] is Ckd by [" + connection_info.getNeighbourBit() +"]");
                            //handle choke message
                            chokeMessageControl((Realistic) message);
                        } catch (IOException e) {
                            System.out.println("Exception in  choke and log: "+e);
                        }
                    }else if(actMsg.messageTypeGet() == 1){
                        //Checking if message type is unchoke, then update log and handle
                        try {
                            //log and handle unchoke mesg
                            peer.log_Writer("At time: "+ Instant.now() + "]: Peer- " + peer.getNeighbourBit() +" is unchoked by " + connection_info.getNeighbourBit() );
                            //handle unchoke message
                            unchokeMessageControl((Realistic) message);
                        } catch (IOException | InterruptedException e) {
                            System.out.println("Exception in unchoke and log: "+e);
                        }
                    }else if (actMsg.messageTypeGet() == 2){
                        //Checking if message type is interested, then update log and handle
                        try {
                            peer.log_Writer("At time: ["+ Instant.now() + "]: Peer- " + peer.getNeighbourBit() +" received msg - ‘accepted’ from " + connection_info.getNeighbourBit() );
                            //handle interested message
                            interestedMessageControl((Realistic) message);//handle Interested Message
                        } catch (IOException e) {
                            System.out.println("Exception in intrested msg and log: "+e);
                        }
                    }else if(actMsg.messageTypeGet() == 3){
                        //Checking if message type is not interested, then update log and handle
                        try {
                            peer.log_Writer("Time: ["+ Instant.now() + "]: Peer " + peer.getNeighbourBit() +" received  ‘not-accepted’ message from " + connection_info.getNeighbourBit());
                            //handle not interested message
                            notInterestedMessageControl((Realistic) message);//handle NotInterested Message
                        } catch (IOException e) {
                            System.out.println("Exception in not intrested  and log: "+e);
                        }
                    }else if(actMsg.messageTypeGet() == 4){
                        //Checking if message type is have, then update log and handle
                        try {
                            peer.log_Writer("Time: ["+ Instant.now() + "]: Peer " + peer.getNeighbourBit() +" received  ‘have’ message from " + connection_info.getNeighbourBit() +" for  piece: " + ((Realistic) message).messagePayloadGet());
                            //handle Have Message
                            Msgexists((Realistic) message);
                        } catch (IOException e) {
                            System.out.println("Exception in have msg and log: "+e);
                        }
                    }else if (actMsg.messageTypeGet() == 5){
                        //Checking if message type is bitfield, then handle
                        try {
                            //handle BitField Message
                            bitFieldMessageControl((Realistic) message);
                        } catch (IOException e) {
                            System.out.println("Exception in bitfield and log: "+e);
                        }
                    }else if (actMsg.messageTypeGet() == 6){
                        //Checking if message type is request
                        try {
                            //handle Request Message
                            ReqmsgControl((Realistic) message);
                        } catch (IOException e) {
                            System.out.println("Exception in  request msg and log: "+e);
                        }
                    }else if (actMsg.messageTypeGet() == 7){
                        //Checking if message is a piece message
                        try {
                            peer.log_Writer("Time: ["+ Instant.now() + "]: Peer " + peer.getNeighbourBit() +"  downloaded  piece " + lReq + " from " + connection_info.getNeighbourBit() +". Tot pieces: "+(peer.getPieceAtIndex().size() + 1));
                        } catch (IOException e) {
                            System.out.println("Exception in piece log: "+e);
                        }
                    }
                }
            }
        }
        ////////////////
    }

    public void HSprocessor(HandShake hs_obj ) throws IOException {
        //Retrieving Handshake Value
        Map<String, String> hs_status = peer.retrieveHSVal();
        //Retrieving Bitfield Value
        Map<String, String> statusOfBF = peer.retrieveBFVal();
//        ObjectOutputStream out = connection_info.getOut();
        boolean pieceExists = peer.pieceExists();
        // If handshake received then validate it and send handshake and b_f.
        if(hs_status.getOrDefault(connection_info.getNeighbourBit(), "False").compareTo("False") == 0) {
            connection_info.setPeerIdentifier(hs_obj .getNeighbourBit());// setting pID here;
            peer. retrieveP2PCon().put(hs_obj .getNeighbourBit(), connection_info);
            if(hs_obj .getHeader().compareTo("P2PFILESHARINGPROJ") == 0) {
                connection_info.sendMessage(new HandShake(peer.getNeighbourBit()));
                if(pieceExists) {
                    connection_info.sendMessage(new Realistic(5, String.valueOf(peer.retrieve_BF())));
                    System.out.println("Handshake validated, sending bitfield");
                    statusOfBF.put(connection_info.getNeighbourBit(), "Sent");
                }
                else {
                    System.out.println("Handshake validated but no bitfield to send");
                }
            }
        }
        // handshake response received, validate and send b_f if you have pieces.
        else if(hs_status.getOrDefault(connection_info.getNeighbourBit(), "False").compareTo("Sent") == 0) {
            if(hs_obj .getHeader().compareTo("P2PFILESHARINGPROJ") == 0 && hs_obj .getNeighbourBit().compareTo(connection_info.getNeighbourBit()) == 0) {
                if(pieceExists) {
                    connection_info.sendMessage(new Realistic(5, String.valueOf(peer.retrieve_BF())));

                    statusOfBF.put(connection_info.getNeighbourBit(), "Sent");
                    System.out.println("Handshake validated and bitfield sent");
                }
                else{
                    System.out.println("Handshake validated but no bitfield sent");
                }
            }
        }
        hs_status.put(connection_info.getNeighbourBit(), "True");
        //Updating Handshake Status field hs_status
        peer.setHSVal(hs_status);
        //Updating Bitfield Status field statusOfBF
        peer.BFValSetter(statusOfBF);
        peer. retrieveDownloadRt().put(connection_info.getNeighbourBit(), 0);
    }

    public void chokeMessageControl(Realistic message) {
        //Add to list of Choked peers
        peer.getCkdPr().add(connection_info.getNeighbourBit());
        //Remove  from list of unchoked peers
        peer.unCkd_prs().remove(connection_info.getNeighbourBit());
    }


    public void unchokeMessageControl(Realistic message) throws IOException, InterruptedException {
        if(unChokeThread == null) {
            unChokeThread = new Thread(new Runnable() {
                public void run() {
                    try {

                        String pID = connection_info.getNeighbourBit();
                        Set<Integer> reqed = peer.reqg_t();
//
                        Collections.shuffle(pieces);
                        while(true) {
                            while(!peer.getCkdPr().contains(pID) && !pieces.isEmpty()) { // add break on receiving all pieces
                                int piece = pieces.remove();
                                //check if peice is present
                                if(peer.retrieve_BF()[piece] == '0' && !reqed.contains(piece)) {
                                    connection_info.sendMessage(new Realistic(6, Integer.toString(piece)));
                                    System.out.println("Asking for piece " + piece);
                                    //ask for it and place it in requested
                                    reqed.add(piece);
                                    //added
                                    lReq = piece;
                                    //if it has the piece always do
                                    while(reqed.contains(piece)) {
                                        if(peer.getCkdPr().contains(pID)) {
                                            reqed.remove(piece);
                                            //remove from requested
                                            pieces.add(piece);
                                            //add to total pieces
                                            break;
                                        }
                                    }
                                    Thread.sleep(50);
                                    ///////sleep for 50ms
                                }
                                else{
                                    System.out.println(piece+" "+connection_info.getNeighbourBit()+" "+ Instant.now());
                                }
                            }
                            while(!peer.unCkd_prs().contains(pID)) {
                                Thread.sleep(100);
                            }

                        }
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Unchoke Message Exception: "+e);
                    }

                }
            });
            //start unchoking thread
            unChokeThread.start();
        }
        //Remove from Choked Peers List
        peer.getCkdPr().remove(connection_info.getNeighbourBit());
        //Add to Unchoked Peers List
        peer.unCkd_prs().add(connection_info.getNeighbourBit());
    }

    public void interestedMessageControl(Realistic message) {
        System.out.println("add to accepted list");
        peer.retrieve_Interested_Key().add(connection_info.getNeighbourBit());
    }

    public void notInterestedMessageControl(Realistic message) {
        //Remove from list of interested peers
        peer.retrieve_Interested_Key().remove(connection_info.getNeighbourBit());
    }

    public void Msgexists(Realistic actMsg) throws IOException {
        int piece = Integer.parseInt(actMsg.messagePayloadGet());
        char[] b_f = peer.retrieve_BF();
        //send piece is present send accept or not accept msg
        if(b_f[piece] == '0') {
            pieces.add(piece);
            connection_info.sendMessage(new Realistic(2, null));
        }
//
        ConcurrentMap<String, String> map = peer.retrieve_BM_Val();
        ////bitfield map
        String bitFieldOfReceivedPeer = map.get(connection_info.getNeighbourBit());
        if(bitFieldOfReceivedPeer == null) {
            // if it is empty
            char[] defaultBitField = new char[Configuration_Setter.pieces_total];
            Arrays.fill(defaultBitField, '0');
            //update bitfield

            bitFieldOfReceivedPeer = String.valueOf(defaultBitField);
        }
        char[] ch = bitFieldOfReceivedPeer.toCharArray();
        ch[piece] = '1';
        map.put(connection_info.getNeighbourBit(), String.valueOf(ch));

    }

    public void bitFieldMessageControl(Realistic actMsg) throws IOException {
        Map<String, String> statusOfBF = peer.retrieveBFVal();
//        ObjectOutputStream out = connection_info.getOut();

        // checking if i am having pieces or not and sending b_f message if i have pieces
        if(statusOfBF.getOrDefault(connection_info.getNeighbourBit(), "False").compareTo("False") == 0) {
            if(peer.pieceExists()) {
                System.out.println("Sending bitfield");
                connection_info.sendMessage(new Realistic(5, String.valueOf(peer.retrieve_BF())));
//                out.flush();
            }
            else{// As of now sending b_f even if 00000 so as to handle null while fetching b_f of peer
                //using retrieve_BM_Val result (handle piece to broadcast by checking)
                // and result (updating b_f when have is received)
                System.out.println("Sending bitfield");
                connection_info.sendMessage(new Realistic(5, String.valueOf(peer.retrieve_BF())));
            }
        }
        String BitFMsg = actMsg.messagePayloadGet();
        String btFd = String.valueOf(peer.retrieve_BF());
        peer.retrieve_BM_Val().put(connection_info.getNeighbourBit(), BitFMsg);

        // adding piece indexes from received b_f to pieces result message handler only if you dont have it result your b_f.
        for(int i = 0; i < BitFMsg.length(); i++) {
            if(BitFMsg.charAt(i) == '1' && btFd.charAt(i) == '0')
                pieces.add(i);
        }

        // checking to send accepted / Not accepted Message
        for(int i = 0; i < btFd.length(); i++) {
            if(BitFMsg.charAt(i) == '1' && BitFMsg.charAt(i) != btFd.charAt(i)) {
                System.out.println("Sending Interested Message");
                connection_info.sendMessage(new Realistic(2));
//                out.flush();
                return;
            }
        }
        System.out.println("Sending Not Interested Message");
        connection_info.sendMessage(new Realistic(3));
//        out.flush();
    }

    public void ReqmsgControl(Realistic actMsg) throws IOException {
        if(!peer.retrieveChokedKey().contains(connection_info.getNeighbourBit())) {
            int reqPc = Integer.parseInt(actMsg.messagePayloadGet());
            byte[] piece = peer.getPieceAtIndex().get(reqPc);
            connection_info.sendMessage(new PieceMessage(7, piece));
            peer. retrieveDownloadRt().put(connection_info.getNeighbourBit(), peer. retrieveDownloadRt().getOrDefault(connection_info.getNeighbourBit(), 0) + 1);
        }
    }

    public void pieceMessageControl(PieceMessage actMsg) throws IOException {

        int pcIdx = lReq;
        char[] ch = peer.retrieve_BF();
        ch[pcIdx] = '1';
        peer.BF_Setter(ch);
        peer.getPieceAtIndex().put(pcIdx, actMsg.getPiece());
        peer.reqg_t().remove(pcIdx);

        //broadcast have message for that piece
        for(String id_peer : peer. retrieveP2PCon().keySet()) {
            PropertiesOfConnection connection_info = peer. retrieveP2PCon().get(id_peer);
            String btFd = peer.retrieve_BM_Val().get(id_peer);
            System.out.println("sending have to " + id_peer +"having b_f" + btFd );
            connection_info.sendMessage(new Realistic(4, Integer.toString(pcIdx)));

        }

        if(peer.getPieceAtIndex().size() == Configuration_Setter.pieces_total) {
            for(String id_peer : peer. retrieveP2PCon().keySet()) {
                PropertiesOfConnection connection_info = peer. retrieveP2PCon().get(id_peer);
                connection_info.sendMessage(new Realistic(3, null));
            }
            peer.log_Writer("At time:["+ Instant.now() + "]: Peer " + peer.getNeighbourBit() +"  downloaded  complete file");
            return;
        }

        ConcurrentMap<String, String> map = peer.retrieve_BM_Val();
        String bitFieldOfReceivedPeer = map.get(connection_info.getNeighbourBit());
        char[] myBitField = peer.retrieve_BF();
        boolean hasPcStr = false;
        for(int i = 0; i < bitFieldOfReceivedPeer.length(); i++) {
            if(myBitField[i] == '0' && bitFieldOfReceivedPeer.charAt(i) == '1') {
                hasPcStr = true;
                break;
            }
        }
        if(!hasPcStr)
            connection_info.sendMessage(new Realistic(3));
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class Node {
    //Setting up peer detail keys
    Configuration_Setter configuration_Setter;
    String id_peer;
    char[] b_f = new char[Configuration_Setter.pieces_total];
    String hostName;
    int portnum;
    ServerInformation svr;
    List<PropertiesOfConnection> connts = new ArrayList<>();
    List<peerInformation> totalPrs = new ArrayList<>();

    ConcurrentMap<String, String> BtFMap = new ConcurrentHashMap<>();

    List<peerInformation> connection_prs = new ArrayList<>();
    Set<String> accepted = Collections.synchronizedSet(new HashSet<>());
    Set<String> unCkd = Collections.synchronizedSet(new HashSet<>());

    Set<String> Ckd = Collections.synchronizedSet(new HashSet<>());
    ConcurrentMap<String, Integer> download_spd = new ConcurrentHashMap<>();
    ConcurrentMap<String, PropertiesOfConnection> prConnect = new ConcurrentHashMap<>();

    Set<String> prfNbr = Collections.synchronizedSet(new HashSet<>());
    Set<String> rstNeighbourselect = Collections.synchronizedSet(new HashSet<>());
    ConcurrentMap<Integer, byte[]> ele_index = new ConcurrentHashMap<>();

    PriorityBlockingQueue<select_a_node> selectionNodes;
    Set<Integer> reqed = Collections.synchronizedSet(new HashSet<>());

    FileWriter fw;
    BufferedWriter bw;
    boolean initFileexists;

    Set<String> unCkdfrPr = Collections.synchronizedSet(new HashSet<>());
    Set<String> ckdFrPr = Collections.synchronizedSet(new HashSet<>());

    public Set<String> unCkd_prs() {
        return unCkdfrPr;
    }

    public void setUnckd_prs(Set<String> unCkdfrPr) {
        this.unCkdfrPr = unCkdfrPr;
    }

    public Set<String> getCkdPr() {
        return ckdFrPr;
    }

    public void setCkd_prs(Set<String> ckdFrPr) {
        this.ckdFrPr = ckdFrPr;
    }

    public boolean initFileExistsG_t() {
        //Checking for file existence
        return initFileexists;
    }

    public void initFileExists_st(boolean initFileexists) {
        //Updating status of file existence
        this.initFileexists = initFileexists;
    }

    public Set<Integer> reqg_t() {
        return reqed;
    }

    public void reqs_t(Set<Integer> reqed) {
        this.reqed = reqed;
    }

    public PriorityBlockingQueue<select_a_node> SelectG_t() {
        //Retrieve blocking Queue list
        return selectionNodes;
    }

    public void Selects_t(PriorityBlockingQueue<select_a_node> selectionNodes) {
        this.selectionNodes = selectionNodes;
    }

    public ConcurrentMap<Integer, byte[]> getPieceAtIndex() {
        return ele_index;
    }

    public void setPieceAtIndex(ConcurrentMap<Integer, byte[]> ele_index) {
        //Setting Bitfield value for every piece with ele_index
        this.ele_index = ele_index;
    }

    public Set<String> BestNbrg_t() {
        return rstNeighbourselect;
    }

    public void BestNbrs_t(Set<String> rstNeighbourselect) {
        this.rstNeighbourselect = rstNeighbourselect;
    }

    public void setConfig(Configuration_Setter configuration_Setter) {
        this.configuration_Setter = configuration_Setter;
    }

    public void setConnections(List<PropertiesOfConnection> connts) {
        this.connts = connts;
    }

    public void setAllPeers(List<peerInformation> totalPrs) {
        //Setting total peers
        this.totalPrs = totalPrs;
    }

    public void BitFields_t(ConcurrentMap<String, String> BtFMap) {
        //Mapping Bitfield values
        this.BtFMap = BtFMap;
    }

    public void setPeersToConnect(List<peerInformation> connection_prs) {
        this.connection_prs = connection_prs;
    }

    public void nonChkKeySetter(Set<String> unCkd) {
        //Setting Unchoked status of peer
        this.unCkd = unCkd;
    }

    public void  chokeKeySetter(Set<String> Ckd) {
        //Setting Choked status of peer
        this.Ckd = Ckd;
    }

    public void PeerToConnSetter(ConcurrentMap<String, PropertiesOfConnection> prConnect) {
        this.prConnect = prConnect;
    }

    public void  downloadRSetter(ConcurrentMap<String, Integer> download_spd) {
        this.download_spd = download_spd;
    }

    public Set<String>  retrievePrefNeighbours() {
        return prfNbr;
    }

    public void   prefNeighbourSetter(Set<String> prfNbr) {
        this.prfNbr = prfNbr;
    }

    public void  interestedKeySetter(Set<String> accepted) {
        this.accepted = accepted;
    }

    public ConcurrentMap<String, PropertiesOfConnection>  retrieveP2PCon() {
        return prConnect;
    }

    public ConcurrentMap<String, Integer>  retrieveDownloadRt() {
        return download_spd;
    }

    public Set<String>  nonChkdKeySetter() {
        return unCkd;
    }

    public Set<String> retrieveChokedKey() {
        return Ckd;
    }

    volatile Queue<MQElem> msgQ = new LinkedList<>();
    boolean verifySelectedNeighbours;
    MsgProcessor mssgController;
    Map<String, String> hs_status = new HashMap<>();
    Map<String, String> statusOfBF = new HashMap<>();

    public Map<String, String> retrieveBFVal() {
        return statusOfBF;
    }

    public void BFValSetter(Map<String, String> statusOfBF) {
        this.statusOfBF = statusOfBF;
    }

    public Map<String, String> retrieveHSVal() {
        return hs_status;
    }

    public void setHSVal(Map<String, String> hs_status) {
        this.hs_status = hs_status;
    }

    public MsgProcessor retrieveHMessage() {
        //Return Message Controller
        return mssgController;
    }

    public void MHSetter(MsgProcessor mssgController) {
        //Setting Message Controller key
        this.mssgController = mssgController;
    }

    public boolean verifySelectedNeighbours() {
        return verifySelectedNeighbours;
    }

    public void SNSetter(boolean selectingNeighbors) {
        verifySelectedNeighbours = selectingNeighbors;
    }

    public Queue<MQElem > retrieve_MQ() {
        return msgQ;
    }

    public void MQ_Setter(Queue<MQElem> msgQ) {
        this.msgQ = msgQ;
    }

    public List<PropertiesOfConnection> retrieve_Conn() {
        //Retrieve Connection Details
        return connts;
    }

    public Set<String> retrieve_Interested_Key() {
        //Return Interested status
        return accepted;
    }

    public List<peerInformation> retrieve_P2Conn() {
        return connection_prs;
    }

    public Configuration_Setter retrieve_Config() {
        return configuration_Setter;
    }

    public Peer(String id_peer, Configuration_Setter configuration_Setter) throws IOException {
        this.id_peer = id_peer;
        this.configuration_Setter = configuration_Setter;
        this.fw = new FileWriter("log_peer_[" + id_peer +"].log");
        this.bw = new BufferedWriter(fw);
    }

    public FileWriter retrieve_FileWriter() {
        return fw;
    }

    public void fileWriter_Key_Setter(FileWriter fw) {
        this.fw = fw;
    }

    public BufferedWriter getBw() {
        return bw;
    }

    public void setBw(BufferedWriter bw) {
        this.bw = bw;
    }

    public void log_Writer(String message) throws IOException {
        synchronized (fw) {
            this.bw.write(message);
            this.bw.newLine();
        }
    }

    public ConcurrentMap<String, String> retrieve_BM_Val() {
        //Retrieve BitField Mapping
        return BtFMap;
    }

    public List<peerInformation> retrieve_Peers() {
        //retrieve total peers
        return totalPrs;
    }

    public ServerInformation retrieve_Srv() {
        //Retrieve svr value
        return svr;
    }

    public void Srv_Setter(ServerInformation svr) {
        //Setting Server key
        this.svr = svr;
    }

    public String retrieve_HN() {
        return hostName;
    }

    public void HN_Setter(String hostName) {
        //Updating Hostname value
        this.hostName = hostName;
    }

    public int getPortNumber() {
        //Retrieving Port NUmber
        return portnum;
    }

    public void set_PortNumber(int portnum) {
        //Setting port number value
        this.portnum = portnum;
    }

    public String getNeighbourBit() {
        //Retrieving Peer ID
        return id_peer;
    }

    public void threadIDSetter(String id_peer) {
        //Setting Peer ID
        this.id_peer = id_peer;
    }

    public char[] retrieve_BF() {
        //Retrieving BitField value
        return b_f;
    }

    synchronized public void BF_Setter(char[] b_f) {
        //Updating Bitfield value
        this.b_f = b_f;
    }

    public Peer() {
    }

    boolean pieceExists() {
        //Checking if peer has the piece
        char[] btFd = this.retrieve_BF();
        for(int i = 0; i < btFd.length; i++){
            if(btFd[i] == '1')
                return true;
        }
        return false;
    }

    public void retrieve_Peer_Details() throws FileNotFoundException {
        //Reading PeerInfo.cfg for list of peers and file existence status
        BufferedReader b_R = new BufferedReader(new FileReader("/Users/simhadrisripriya/Downloads/CNProject 3/src/com/company/PeerInfo.cfg"));
        String line = null;
        try {
            boolean is_P2P_Conn = true;
            while((line = b_R.readLine()) != null) {
                String[] str_split = line.split(" ");
                //call peer data class
                peerInformation p_Det = new peerInformation(str_split[0], str_split[1], Integer.parseInt(str_split[2]), Integer.parseInt(str_split[3]));
                if(p_Det.getNeighbourBit().compareTo(this.id_peer) == 0) {
                    p_Det_Setter(p_Det);
                    is_P2P_Conn = false;
                }
                if(is_P2P_Conn) {
                    connection_prs.add(p_Det);
                }
                totalPrs.add(p_Det);
                Ckd.add(p_Det.getNeighbourBit());
            }
        }catch (FileNotFoundException ex1) {
            System.out.println("PeerInfo.cfg file not found:" +ex1);
        }catch (IOException ex2) {
            System.out.println("File Read Exception: " +ex2);
        }finally {
            try {
                b_R.close();
            } catch (IOException e) {
                System.out.println("Exception: " +e);
            }
        }
    }

    public void p_Det_Setter(peerInformation p_Det) {
        //Setting Peer details
        char[] b_f = new char[Configuration_Setter.pieces_total];
        if(p_Det.fileext_gt() == 1) {
            Arrays.fill(b_f, '1');
        }
        else {
            Arrays.fill(b_f, '0');
        }
        initFileExists_st(p_Det.fileext_gt() == 1);
        BF_Setter(b_f);
        set_PortNumber(p_Det.getPortNumber());
        HN_Setter(p_Det.getNodeName());
    }
}

//_______________________________________________________________________________________________________________________________________________________________

// Entry point

//main process of the peers


//______________________________________________________________________________________________________________________________________________________________
class MQElem {
    PropertiesOfConnection connection_info;
    Object message;

    public MQElem(PropertiesOfConnection connection_info, Object message) {
        this.connection_info = connection_info;
        this.message = message;
    }

    public PropertiesOfConnection retrieve_Conn_Det() {
        return connection_info;
    }

    public void Con_Det_Setter(PropertiesOfConnection connection_info) {
        this.connection_info = connection_info;
    }

    public Object retrieve_Msg() {
        return message;
    }

    public void Msg_Setter(Object message) {
        this.message = message;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class Realistic implements Serializable {
    private int msg_size;
    private int messageType;
    private String messagePayload;
    private static final long serialVersionUID = 8983558202217591746L;

    public Realistic() {}

    public Realistic(int messageType, String messagePayload) {
        this.messageType = messageType;
        this.messagePayload = messagePayload;
        //@ Recieved Null pointer because of this
        if(messagePayload != null)
            this.msg_size = messagePayload.length() + 1;
        else
            this.msg_size = 1;
    }

    public Realistic(int messageType) {
        this.messageType = messageType;
        this.msg_size = 1;
    }

    public int GetML() {
        //Retrieve Message Length
        return msg_size;
    }

    public void SetML(int msg_size) {
        //Update the Message size
        this.msg_size = msg_size;
    }

    public int messageTypeGet() {
        //Retrieve type of message
        return messageType;
    }

    public void MSGTYst(int messageType) {
        this.messageType = messageType;
    }

    public String messagePayloadGet() {
        //Retrieving the Message Payload
        return messagePayload;
    }

    public void messagePayloadSet(String messagePayload) {
        //Updating the Message payload
        this.messagePayload = messagePayload;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class ServerInformation extends Thread{

    private static int sPort;
    Peer peer;

    public ServerInformation(int portnum, Peer peer) {
        sPort = portnum;
        this.peer = peer;
    }

    public void run() {
        System.out.println("Server is running.");
        ServerSocket listener = null;
        try {
            listener = new ServerSocket(sPort);
            int clientNum = 1;
            System.out.println("In server " + Thread.currentThread().getName());
            while(true) {
                Socket socket = listener.accept();
                System.out.println("Client- "  + clientNum + "  connected!");
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                ObjectInputStream result = new ObjectInputStream(socket.getInputStream());
                String peerIdConnected = Integer.toString(clientNum);
                PropertiesOfConnection connection_info = new PropertiesOfConnection(peerIdConnected, socket, out, result, peer, new ConcurrentLinkedQueue<Object>());
                peer.retrieve_Conn().add(connection_info);

                connection_info.start();
                clientNum++;
            }
        } catch (IOException e) {
            System.out.println("Server Exception:"+e);
        }
        try {

        } finally {
            try {
                listener.close();
            } catch (IOException e) {
                System.out.println("Exception:"+e);
            }
        }
    }
}

