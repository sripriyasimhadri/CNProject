package com.company;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.net.*;
import java.util.concurrent.*;

//_______________________________________________________________________________________________________________________________________________________________

//sett download rates
public class peerProcess {
    static com.company.Peer peer;
    static String peerID;
    public static void main(String[] args) throws Exception {
        peerID = "1002";
        if(args.length != 0)
            peerID = args[0];
        peer = new com.company.Peer(peerID, new Config());
        peer.retrieve_Config();
        peer.retrieve_Peer_Details();
        pecSelectAlgo();
        if(peer.initFileExistsG_t())
            SptFile();
        com.company.Srvercls svr = new com.company.Srvercls(peer.portnum_gt(), peer);
        peer.Srv_Setter(svr);
        svr.start();

        for(com.company.peerData peerInfoServer : peer.retrieve_P2Conn()) {
            Socket reqSocket = null;
            try {
                reqSocket = new Socket(peerInfoServer.getHost(), peerInfoServer.portnum_gt());
                //connecting to socket with socke class
                ObjectOutputStream out = new ObjectOutputStream(reqSocket.getOutputStream());
                //output stream to socket
                //log connection
                System.out.println("Established connection with peer -  " + peerInfoServer.getHost() + " at port number:" + " " + peerInfoServer.portnum_gt());
                //input stream to socket
                ObjectInputStream inreslt = new ObjectInputStream(reqSocket.getInputStream());
                //create handshake msg to be sent
                com.company.HandShake handshakeObj = new com.company.HandShake(peer.getPid());
                //write handshake to the object
                out.writeObject(handshakeObj);
                //log connection
                peer.log_Writer("At Time: ["+ Instant.now() + "]: PeerID: [" + peerID +"] connected to PeerID: [" + peerInfoServer.getPid()+"]");
                System.out.println("Handshake message sent from "+ peerID+" to "+ peerInfoServer.getPid());
                //create hashmap to update status
                Map<String, String> statusOfHS = peer.retrieveHSVal();
                statusOfHS.put(peerInfoServer.getPid(), "Sent"); //updating
                peer.setHSVal(statusOfHS);
                //object to ConnectionDetails
                com.company.ConnectionDetails contInfo = new com.company.ConnectionDetails(peerInfoServer.getPid(), reqSocket, out, inreslt, peer, new ConcurrentLinkedQueue<Object>());
                //Add them to peer
                peer.retrieve_Conn().add(contInfo);
                peer. retrieveP2PCon().put(peerInfoServer.getPid(), contInfo);
                contInfo.start();
            } catch (IOException e) {

                System.out.println("Exception In socket establishment and handshake: "+e);
            }
        }
        timeIt();
        System.out.println("Done");
        System.exit(0);
    }

    public static void timeIt() throws InterruptedException {
        Thread timeIt = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Thread: " + Thread.currentThread().getName());
                final ScheduledExecutorService preferredNeighborsScheduler = Executors.newScheduledThreadPool(1);
                final ScheduledExecutorService optimisticNeighborScheduler = Executors.newScheduledThreadPool(1);
                Runnable prfNbr = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            msgChocked();
                            getNbrMsg();
                            msgUnchoked();
                            StringBuilder strBuildObj = new StringBuilder("At time: ["+ Instant.now() + "]: the list of preferred neighbours of PeerID [" + peerID +"] is [");
                            for(String s : peer. retrievePrefNeighbours()) {
                                strBuildObj.append(s + ", ");
                            }
                            strBuildObj.append("]");
                            peer.log_Writer(strBuildObj.toString());
                        } catch (IOException e) {
                            System.out.println("Exception in Preferred Neighbours List: " +e);
                        }
                    }
                };
                Runnable rstNeighbourselect = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            getBestNbr();
                            StringBuilder strBuildObj = new StringBuilder("At time ["+ Instant.now() + "]: list of Optimistic unchoked neighbors for PeerID [" + peerID +"] is [");
                            for(String s : peer.BestNbrg_t()) {
                                strBuildObj.append(s + ", ");
                            }
                            strBuildObj.append("]");
                            peer.log_Writer(strBuildObj.toString());
                        } catch (IOException e) {
                            System.out.println("Exception in Optimistic Unchoked Neighbours: "+e);
                        }
                    }
                };
                final ScheduledFuture<?> PNH = preferredNeighborsScheduler.scheduleAtFixedRate(prfNbr, 0, Config.unChokingInt, TimeUnit.SECONDS);
                final ScheduledFuture<?> ONH = optimisticNeighborScheduler.scheduleAtFixedRate(rstNeighbourselect, 1, Config.opUnchkInt, TimeUnit.SECONDS);

                while(true) {
                    try {
                        Thread.sleep(3000);
                        if(completedBool()) {
                            System.out.println("Stop timer");
                            //ended here
                            PNH.cancel(true);
                            ONH.cancel(true);
                            break;
                        }
                    } catch (InterruptedException e) {
                        System.out.println("Timer Exception:" +e);
                    } catch (IOException e) {
                        System.out.println("General Exception:" +e);
                    }
                }
            }
        });

        while(peer.retrieve_Interested_Key().size() == 0) {
            //wait 1000ms
            Thread.sleep(1000);
        }
        checkTime.start();
        while(checkTime.isAlive()) {
            //wait 1000ms
            Thread.sleep(1000);
        }
    }

    public static void msgChocked() throws IOException {
        //Determining new set of neighbours to choke from previous list of preferred neighbours
        Set<String> unCkd = peer. unChokedKeySetter();
        Set<String> Ckd = peer.retrieveChokedKey();
        Map<String, com.company.ConnectionDetails> prConnect = peer. retrieveP2PCon();
        Set<String> prfNbr = peer. retrievePrefNeighbours();

        for(String peerID : prfNbr) {
            com.company.ConnectionDetails contInfo = prConnect.get(peerID);
            contInfo.send(new com.company.ActualMessage(0, null));
            //Updating Choked list
            Ckd.add(peerID);
            //Updating Unchoked list
            unCkd.remove(peerID);
        }

        prfNbr.clear();
        peer.prefNeighbourSetter(prfNbr);
        peer.unChokeKeySetter(unCkd);
        peer.chokeKeySetter(Ckd);
    }

    public static void getNbrMsg() throws IOException {
        Set<String> Ckd = peer.retrieveChokedKey();
        Set<String> unCkd = peer.unChokedKeySetter();
        Set<String> accepted = peer.retrieve_Interested_Key();
        Set<String> prfNbr = new HashSet<>();
        ConcurrentMap<String, Integer> download_spd = peer.retrieveDownloadRt();
        PriorityQueue<com.company.Downloasdrate_n> priorityQ = new PriorityQueue<com.company.Downloasdrate_n>((a, b) -> b.download_spd - a.download_spd);

        for(String key : download_spd.keySet()) {
            priorityQ.add(new com.company.Downloasdrate_n(key, download_spd.get(key)));

            download_spd.put(key, 0);
        }

        int prefNbrNum = Config.numberOfPrefNeighbors;
        while(prefNbrNum > 0 && priorityQ.size() != 0) {
            com.company.Downloasdrate_n node = priorityQ.poll();
            /*Unchoking k preferred neighbours based on Download rate during the previous
             unchoking interval*/
            if(accepted.contains(node.pID)) {
                //Updating list of Choked, Unchoked and preferred neighbours
                unCkd.add(node.pID);
                Ckd.remove(node.pID);
                prfNbr.add(node.pID);
                //Decrement till k preferred neighbours are reached
                prefNbrNum--;
            }
        }

        peer.prefNeighbourSetter(prfNbr);
        peer.unChokeKeySetter(unCkd);
        peer.chokeKeySetter(Ckd);
        peer.interestedKeySetter(accepted);
        peer.downloadRSetter(download_spd);
    }

    public static void msgUnchoked() throws IOException {
        Map<String, com.company.ConnectionDetails> prConnect = peer. retrieveP2PCon();

        for(String pID : peer.retrievePrefNeighbours()) {
            com.company.ConnectionDetails contInfo = prConnect.get(pID);
            contInfo.send(new com.company.ActualMessage(1, null));
        }

    }

    public static void getBestNbr() throws IOException {

        Set<String> rstNeighbourselect = peer.BestNbrg_t();
        Set<String> accepted = peer.retrieve_Interested_Key();
        Set<String> Ckd = peer.retrieveChokedKey();
        Set<String> unCkd = peer.unChokedKeySetter();
        List<String> interestedAndChoked = new ArrayList<>();
        Map<String, com.company.ConnectionDetails> prConnect = peer.retrieveP2PCon();

        for(String pID : rstNeighbourselect) {
            com.company.ConnectionDetails contInfo = prConnect.get(pID);
            contInfo.send(new com.company.ActualMessage(0, null));
            rstNeighbourselect.remove(pID);
            Ckd.add(pID);
        }

        for(String pID : accepted) {
            if(Ckd.contains(pID)) {
                interestedAndChoked.add(pID);
            }
        }

        Collections.shuffle(interestedAndChoked);

        if(interestedAndChoked.size() != 0) {
            String pID = interestedAndChoked.get(0);
            com.company.ConnectionDetails contInfo = prConnect.get(pID);
            contInfo.send(new com.company.ActualMessage(1, null));
            unCkd.add(pID);
            Ckd.remove(pID);
//            accepted.remove(pID);
            rstNeighbourselect.add(pID);
        }

        peer.chokeKeySetter(Ckd);
        peer.unChokeKeySetter(unCkd);
        peer.interestedKeySetter(accepted);
        peer.BestNbrs_t(rstNeighbourselect);
    }

    public static void SptFile() throws IOException {
        // Splitting the file into prices based on File and piece size
        File fls = new File("/Users/simhadrisripriya/Downloads/CNProject 3/src/com/company/thefile");
        FileInputStream inStream = new FileInputStream(fls);
        BufferedInputStream bufferStream = new BufferedInputStream(inStream);

        byte[] elements;
        long lenFs = fls.length();
        long curr = 0;
        int idx = 0;

        ConcurrentMap<Integer, byte[]> ele_index = new ConcurrentHashMap<>();

        while(curr < lenFs){
            int size = Config.pieceSize;
            if(lenFs - curr >= size)
                curr += size;
            else{
                size = (int)(lenFs - curr);
                curr = lenFs;
            }
            elements = new byte[size];
            bufferStream.read(elements, 0, size);
            ele_index.put(idx, elements);
            idx++;
        }

        peer.setPieceAtIndex(ele_index);
    }

    public static void join_all_files() throws IOException {
        Map<Integer, byte[]> ele_index = peer.getPieceAtIndex();
        int pieces = Config.totPieces;
        FileOutputStream outStream = new FileOutputStream(Config.fileName);
        for(int i = 0; i < pieces; i++) {
            outStream.write(ele_index.get(i));
        }
    }

    public static void pecSelectAlgo() {

        int pID = Integer.parseInt(peer.getPid());
        int numPrs = peer.retrieve_Peers().size();
        int totalRng = (int) Math.ceil(Config.totPieces / numPrs);
        int min = Math.max(0, (pID % 1000 - 1) * totalRng);
        int max = Math.min(min + totalRng, Config.totPieces);

        PriorityBlockingQueue<com.company.node_slct> priorityQ = new PriorityBlockingQueue<com.company.node_slct>(10, (a, b) -> b.pty - a.pty);

        List<com.company.node_slct> lst = new ArrayList<>();

        for(int i = 0; i < Config.totPieces; i++) {
            if(i >= min && i <= max) {
                lst.add(new com.company.node_slct(2, i));
            }
            else {
                lst.add(new com.company.node_slct(1, i));
            }
        }

        Collections.shuffle(lst);

        for(int i = 0; i < lst.size(); i++) {
            priorityQ.add(lst.get(i));
        }

        peer.Selects_t(priorityQ);

    }

    public static boolean completedBool() throws InterruptedException, IOException {
        List<com.company.peerData> totalPrs = peer.retrieve_Peers();
        int N = 0;
        for(com.company.peerData Pr1 : totalPrs) {
            String btFd;
            if(Pr1.getPid().compareTo(peerID) == 0) {
                btFd = String.valueOf(peer.retrieve_BF());
            }
            else {
                btFd = peer.retrieve_BM_Val().getOrDefault(Pr1.getPid(), null);
            }
            if(btFd == null)
                continue;
            for(int i = 0; i < btFd.length(); i++) {
                if(btFd.charAt(i) == '0')
                    return false;
            }
            //Increment Counter for each peer that has received all pieces
            N++;
        }
        if(N == 3) {
            //Close all threads when all peers have received all the pieces
            Thread.sleep(10000);
            exitAll();
            crtFs();
            return true;
        }
        return false;
    }

    public static void crtFs() throws IOException {
        File fls2 = new File("./peer_" + peerID);
        if (!fls2.exists()){
            fls2.mkdirs();
        }
        ConcurrentMap<Integer, byte[]> pieces = peer.getPieceAtIndex();
        File fls = new File("./peer_" + peerID + "/" + "thefile");
        FileOutputStream stm = new FileOutputStream(fls);
        for(int i = 0; i < Config.totPieces; i++) {
            stm.write(pieces.get(i));
        }
        stm.close();
    }

    public static void exitAll() throws IOException, InterruptedException {
        //Process to end all threads
        ConcurrentMap<String, com.company.ConnectionDetails> connts = peer.retrieveP2PCon();

        for(String peer : connts.keySet()) {
            com.company.ConnectionDetails conn2 = connts.get(peer);
            Thread th1 = (Thread) conn2;
            Thread th2 = (Thread) conn2.retrieveMH();
            Thread unChoke = (Thread) conn2.retrieveMH().uncokThread();
            if(unChoke != null) {
                System.out.println("Peer: "+peer+" unchoke thread "+unChoke.getName()+" closed");
                unChoke.stop();
            }
            Thread.sleep(1000);
            if(th2 != null) {
                System.out.println("Peer: "+peer+" Message handler thread"+th2.getName()+" closed");
                th2.stop();
            }

            if(th1 != null) {
                System.out.println("Peer: "+peer+" Connection thread "+th1.getName()+" closed");
                th1.stop();
            }
        }
        Thread svr = (Thread) peer.retrieve_Srv();
        System.out.println("Peer: "+peer+" Server thread"+svr.getName()+" closed");
        //Stopping Server thread
        svr.stop();
    }
}
class Downloasdrate_n {
    //Storing piece Download rates of peers
    String pID;
    Integer download_spd;
    public Downloasdrate_n(String pID, Integer download_spd) {
        this.pID = pID;
        this.download_spd = download_spd;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

//choose the nodes
class node_slct {
    int pty;
    int idx;

    public node_slct(int pty, int idx) {
        this.pty = pty;
        this.idx = idx;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

//Config file update
class Config {
    //declare all the variables from common.cfg
    static int numberOfPrefNeighbors;
    //unchking interval in log files
    static int unChokingInt;
    //also log file
    static int opUnchkInt;
    //check with small peers first
    static String fileName;
    static int totPieces;
    static int fileSize;
    static int pieceSize;

    public Config() {
        try {
            InputStream is = new FileInputStream("/Users/simhadrisripriya/Downloads/CNProject 3/src/com/company/common.cfg");
            Properties prop = new Properties();
            prop.load(is);
            numberOfPrefNeighbors  = Integer.parseInt(prop.getProperty("NumberOfPreferredNeighbors"));
            unChokingInt           = Integer.parseInt(prop.getProperty("UnchokingInterval"));
            opUnchkInt = Integer.parseInt(prop.getProperty("OptimisticUnchokingInterval"));
            fileName                    = prop.getProperty("FileName");
            fileSize                    = Integer.parseInt(prop.getProperty("FileSize"));
            pieceSize                   = Integer.parseInt(prop.getProperty("PieceSize"));

            if(fileSize%pieceSize == 0){
                totPieces = fileSize/pieceSize;
            } else {
                totPieces = (fileSize/pieceSize) + 1;
            }
        } catch (Exception e) {
            System.out.println("Exception in config class: "+e);
        }
    }

}

//_______________________________________________________________________________________________________________________________________________________________

//get and update peer data
class peerData {
    private int fileexists;
    private String peerID;
    private String host;
    private int portnum;


    public peerData(String peerID, String host, int portnum, int fileexists) {
        this.peerID = peerID;
        this.host = host;
        this.portnum = portnum;
        this.fileexists = fileexists;
    }

    public String getPid() {
        return peerID;
    }

    public void setPid(String peerID) {
        this.peerID = peerID;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int portnum_gt() {
        return portnum;
    }

    public void portnum_st(int portnum) {
        this.portnum = portnum;
    }

    public int fileext_gt() {
        return fileexists;
    }

    public void fileext_st(int fileexists) {
        this.fileexists = fileexists;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

//Get and update connection information
class ConnectionDetails extends Thread {

    private String pID;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream inreslt;
    private boolean isHandShakeDone;
    protected Queue<Object> msgQ;
    private Peer peerLocal;
    private MsgProcessor mssgController;

    public MsgProcessor retrieveMH() {
        return mssgController;
    }

    public Queue<Object> retrieve_MQ() {
        return msgQ;
    }

    public void MQ_Setter(Queue<Object> msgQ) {
        this.msgQ = msgQ;
    }

    public int getLength() {
        return msgQ.size();
    }

    public ConnectionDetails(String pID, Socket socket, ObjectOutputStream out, ObjectInputStream inreslt, Peer peerLocal, Queue<Object> msgQ) {
        this.pID = pID;
        this.socket = socket;
        this.out = out;
        this.inreslt = inreslt;
        this.peerLocal = peerLocal;
        this.msgQ = msgQ;

    }

    public String getPeerId() {
        return pID;
    }

    public void setPeerId(String pID) {
        this.pID = pID;
    }


    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    synchronized public ObjectOutputStream getOut() {
        return out;
    }

    public void setOut(ObjectOutputStream out) {
        this.out = out;
    }

    public ObjectInputStream getIn() {
        return inreslt;
    }

    public void setIn(ObjectInputStream inreslt) {
        this.inreslt = inreslt;
    }

    public boolean isHandShakeDone() {
        return isHandShakeDone;
    }

    public void setHandShakeDone(boolean handShakeDone) {
        this.isHandShakeDone = handShakeDone;
    }

    public void run() {
        try {
            System.out.println("In Connection thread " + Thread.currentThread().getName() + "" + this.hashCode() + " " + msgQ.hashCode());


            this.mssgController = new MsgProcessor(this.peerLocal, this, this.msgQ);
            mssgController.start();
            while (true) {
                Object message = this.inreslt.readObject();

                msgQ.add(message);

            }
        } catch (ClassNotFoundException classNot) {
            System.out.println("Class Not Found Exception:"+classNot);
        } catch (IOException ioException) {
            try {
                peerLocal.getBw().close();
            } catch (IOException e) {
                System.out.println("Buffered Writer Close Exception:"+e);
            }
            System.out.println("Exception:"+ioException);

        }
    }

    synchronized public void send(Object message) throws IOException {
        System.out.print("[Response] : ");
        if (message instanceof HandShake) {
            System.out.println("Sending Handshake message to " + pID);
        } else if (message instanceof ActualMessage) {
            if (((ActualMessage) message).getMessageType() == 7)
                System.out.println("Sending message type " + ((ActualMessage) message).getMessageType() + " Msg: " + ((ActualMessage) message).getMessagePayload().split("-")[0]);
            else
                System.out.println("Sending message  type " + ((ActualMessage) message).getMessageType() + " Msg: " + ((ActualMessage) message).getMessagePayload());
        }
        out.writeObject(message);
        out.flush();
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class PieceMessage extends ActualMessage {
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
    private String peerID;
    private static final long serialVersionUID = 42L;

    public HandShake() {}

    public HandShake(String peerID) {
        this.peerID = peerID;
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

    public String getPid() {
        return peerID;
    }

    public void setPid(String peerID) {
        this.peerID = peerID;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class MsgProcessor extends Thread{
    Peer peer;
    ConnectionDetails contInfo;
    Queue<Object> msgQ;
    LinkedList<Integer> pieces = new LinkedList<>();
    Thread unChokeThread;
    int lReq;

    public Thread uncokThread() {
        return unChokeThread;

    }

    public MsgProcessor(Peer peer, ConnectionDetails contInfo, Queue<Object> msgQ) {

        this.peer = peer;
        this.contInfo = contInfo;
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
                    System.out.println("Received handshake message from " + ((HandShake) message).getPid()+" Message:" + message);
                    try {
                        peer.log_Writer("At time: [" + Instant.now()+"]: Peer [" + peer.getPid()+"] connected after handshake to Peer [" + ((HandShake) message).getPid() + "]");
                        HSprocessor((HandShake) message);
                    } catch (IOException e) {
                        System.out.println("Exception in hanshake processing : "+e);

                    }
                }
                else if (message instanceof  PieceMessage) {
                    PieceMessage pieceMessage = (PieceMessage) message;
                    //If message is a piece message, update log file
                    if(pieceMessage.getMessageType() == 7)
                        System.out.println("At time: ["+Instant.now().toString()+"] Message type - " + pieceMessage.getMessageType() + " received from "+contInfo.getPeerId() +" Msg: " + lReq );
                    try {
                        peer.log_Writer("At time: ["+ Instant.now() + "]: Peer - " + peer.getPid() +"  downloaded  piece -" + lReq + " from - " + contInfo.getPeerId() +". Tot Pieces is "+(peer.getPieceAtIndex().size() + 1));
                        // handle Piece Message
                        pieceMessageControl(pieceMessage);
                    } catch (IOException e) {
                        System.out.println("Exception in writing log: "+e);
                    }
                    break;
                }
                else if (message instanceof ActualMessage){
                    ActualMessage actMsg = (ActualMessage) message;
                    if(actMsg.getMessageType() == 7)
                        System.out.println(" Message type- " + actMsg.getMessageType() + " is recieved from "+contInfo.getPeerId() +"; Msg: " + lReq + " " + Instant.now()
                                .toString());
                    else
                        System.out.println("Message of type " + actMsg.getMessageType() + " is received from "+contInfo.getPeerId() +"; Msg: " + actMsg.getMessagePayload() + Instant.now()
                                .toString());
                    if(actMsg.getMessageType() == 0 ){
                        //Checking if message type is choke, then update log and handle
                        try {
                            peer.log_Writer("["+ Instant.now() + "]: Peer [" + peer.getPid() +"] is Ckd by [" + contInfo.getPeerId() +"]");
                            //handle choke message
                            chokeMessageControl((ActualMessage) message);
                        } catch (IOException e) {
                            System.out.println("Exception in  choke and log: "+e);
                        }
                    }else if(actMsg.getMessageType() == 1){
                        //Checking if message type is unchoke, then update log and handle
                        try {
                            //log and handle unchoke mesg
                            peer.log_Writer("At time: "+ Instant.now() + "]: Peer- " + peer.getPid() +" is unchoked by " + contInfo.getPeerId() );
                            //handle unchoke message
                            unchokeMessageControl((ActualMessage) message);
                        } catch (IOException | InterruptedException e) {
                            System.out.println("Exception in unchoke and log: "+e);
                        }
                    }else if (actMsg.getMessageType() == 2){
                        //Checking if message type is interested, then update log and handle
                        try {
                            peer.log_Writer("At time: ["+ Instant.now() + "]: Peer- " + peer.getPid() +" received msg - ‘accepted’ from " + contInfo.getPeerId() );
                            //handle interested message
                            interestedMessageControl((ActualMessage) message);//handle Interested Message
                        } catch (IOException e) {
                            System.out.println("Exception in intrested msg and log: "+e);
                        }
                    }else if(actMsg.getMessageType() == 3){
                        //Checking if message type is not interested, then update log and handle
                        try {
                            peer.log_Writer("Time: ["+ Instant.now() + "]: Peer " + peer.getPid() +" received  ‘not-accepted’ message from " + contInfo.getPeerId());
                            //handle not interested message
                            notInterestedMessageControl((ActualMessage) message);//handle NotInterested Message
                        } catch (IOException e) {
                            System.out.println("Exception in not intrested  and log: "+e);
                        }
                    }else if(actMsg.getMessageType() == 4){
                        //Checking if message type is have, then update log and handle
                        try {
                            peer.log_Writer("Time: ["+ Instant.now() + "]: Peer " + peer.getPid() +" received  ‘have’ message from " + contInfo.getPeerId() +" for  piece: " + ((ActualMessage) message).getMessagePayload());
                            //handle Have Message
                            Msgexists((ActualMessage) message);
                        } catch (IOException e) {
                            System.out.println("Exception in have msg and log: "+e);
                        }
                    }else if (actMsg.getMessageType() == 5){
                        //Checking if message type is bitfield, then handle
                        try {
                            //handle BitField Message
                            bitFieldMessageControl((ActualMessage) message);
                        } catch (IOException e) {
                            System.out.println("Exception in bitfield and log: "+e);
                        }
                    }else if (actMsg.getMessageType() == 6){
                        //Checking if message type is request
                        try {
                            //handle Request Message
                            ReqmsgControl((ActualMessage) message);
                        } catch (IOException e) {
                            System.out.println("Exception in  request msg and log: "+e);
                        }
                    }else if (actMsg.getMessageType() == 7){
                        //Checking if message is a piece message
                        try {
                            peer.log_Writer("Time: ["+ Instant.now() + "]: Peer " + peer.getPid() +"  downloaded  piece " + lReq + " from " + contInfo.getPeerId() +". Tot pieces: "+(peer.getPieceAtIndex().size() + 1));
                        } catch (IOException e) {
                            System.out.println("Exception in piece log: "+e);
                        }
                    }
                }
            }
        }
        ////////////////
    }

    public void HSprocessor(HandShake handshakeObj) throws IOException {
        //Retrieving Handshake Value
        Map<String, String> statusOfHS = peer.retrieveHSVal();
        //Retrieving Bitfield Value
        Map<String, String> statusOfBF = peer.retrieveBFVal();
//        ObjectOutputStream out = contInfo.getOut();
        boolean pieceExists = peer.pieceExists();
        // If handshake received then validate it and send handshake and b_f.
        if(statusOfHS.getOrDefault(contInfo.getPeerId(), "False").compareTo("False") == 0) {
            contInfo.setPeerId(handshakeObj.getPid());// setting pID here;
            peer. retrieveP2PCon().put(handshakeObj.getPid(), contInfo);
            if(handshakeObj.getHeader().compareTo("P2PFILESHARINGPROJ") == 0) {
                contInfo.send(new HandShake(peer.getPid()));
                if(pieceExists) {
                    contInfo.send(new ActualMessage(5, String.valueOf(peer.retrieve_BF())));
                    System.out.println("Handshake validated, sending bitfield");
                    statusOfBF.put(contInfo.getPeerId(), "Sent");
                }
                else {
                    System.out.println("Handshake validated but no bitfield to send");
                }
            }
        }
        // handshake response received, validate and send b_f if you have pieces.
        else if(statusOfHS.getOrDefault(contInfo.getPeerId(), "False").compareTo("Sent") == 0) {
            if(handshakeObj.getHeader().compareTo("P2PFILESHARINGPROJ") == 0 && handshakeObj.getPid().compareTo(contInfo.getPeerId()) == 0) {
                if(pieceExists) {
                    contInfo.send(new ActualMessage(5, String.valueOf(peer.retrieve_BF())));

                    statusOfBF.put(contInfo.getPeerId(), "Sent");
                    System.out.println("Handshake validated and bitfield sent");
                }
                else{
                    System.out.println("Handshake validated but no bitfield sent");
                }
            }
        }
        statusOfHS.put(contInfo.getPeerId(), "True");
        //Updating Handshake Status field statusOfHS
        peer.setHSVal(statusOfHS);
        //Updating Bitfield Status field statusOfBF
        peer.BFValSetter(statusOfBF);
        peer. retrieveDownloadRt().put(contInfo.getPeerId(), 0);
    }

    public void chokeMessageControl(ActualMessage message) {
        //Add to list of Choked peers
        peer.getCkdPr().add(contInfo.getPeerId());
        //Remove  from list of unchoked peers
        peer.unCkd_prs().remove(contInfo.getPeerId());
    }


    public void unchokeMessageControl(ActualMessage message) throws IOException, InterruptedException {
        if(unChokeThread == null) {
            unChokeThread = new Thread(new Runnable() {
                public void run() {
                    try {

                        String pID = contInfo.getPeerId();
                        Set<Integer> reqed = peer.reqg_t();
//
                        Collections.shuffle(pieces);
                        while(true) {
                            while(!peer.getCkdPr().contains(pID) && !pieces.isEmpty()) { // add break on receiving all pieces
                                int piece = pieces.remove();
                                //check if peice is present
                                if(peer.retrieve_BF()[piece] == '0' && !reqed.contains(piece)) {
                                    contInfo.send(new ActualMessage(6, Integer.toString(piece)));
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
                                    System.out.println(piece+" "+contInfo.getPeerId()+" "+ Instant.now());
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
        peer.getCkdPr().remove(contInfo.getPeerId());
        //Add to Unchoked Peers List
        peer.unCkd_prs().add(contInfo.getPeerId());
    }

    public void interestedMessageControl(ActualMessage message) {
        System.out.println("add to accepted list");
        peer.retrieve_Interested_Key().add(contInfo.getPeerId());
    }

    public void notInterestedMessageControl(ActualMessage message) {
        //Remove from list of interested peers
        peer.retrieve_Interested_Key().remove(contInfo.getPeerId());
    }

    public void Msgexists(ActualMessage actMsg) throws IOException {
        int piece = Integer.parseInt(actMsg.getMessagePayload());
        char[] b_f = peer.retrieve_BF();
        //send piece is present send accept or not accept msg
        if(b_f[piece] == '0') {
            pieces.add(piece);
            contInfo.send(new ActualMessage(2, null));
        }
//
        ConcurrentMap<String, String> map = peer.retrieve_BM_Val();
        ////bitfield map
        String bitFieldOfReceivedPeer = map.get(contInfo.getPeerId());
        if(bitFieldOfReceivedPeer == null) {
            // if it is empty
            char[] defaultBitField = new char[Config.totPieces];
            Arrays.fill(defaultBitField, '0');
            //update bitfield

            bitFieldOfReceivedPeer = String.valueOf(defaultBitField);
        }
        char[] ch = bitFieldOfReceivedPeer.toCharArray();
        ch[piece] = '1';
        map.put(contInfo.getPeerId(), String.valueOf(ch));

    }

    public void bitFieldMessageControl(ActualMessage actMsg) throws IOException {
        Map<String, String> statusOfBF = peer.retrieveBFVal();
//        ObjectOutputStream out = contInfo.getOut();

        // checking if i am having pieces or not and sending b_f message if i have pieces
        if(statusOfBF.getOrDefault(contInfo.getPeerId(), "False").compareTo("False") == 0) {
            if(peer.pieceExists()) {
                System.out.println("Sending bitfield");
                contInfo.send(new ActualMessage(5, String.valueOf(peer.retrieve_BF())));
//                out.flush();
            }
            else{// As of now sending b_f even if 00000 so as to handle null while fetching b_f of peer
                //using retrieve_BM_Val inreslt (handle piece to broadcast by checking)
                // and inreslt (updating b_f when have is received)
                System.out.println("Sending bitfield");
                contInfo.send(new ActualMessage(5, String.valueOf(peer.retrieve_BF())));
            }
        }
        String BitFMsg = actMsg.getMessagePayload();
        String btFd = String.valueOf(peer.retrieve_BF());
        peer.retrieve_BM_Val().put(contInfo.getPeerId(), BitFMsg);

        // adding piece indexes from received b_f to pieces inreslt message handler only if you dont have it inreslt your b_f.
        for(int i = 0; i < BitFMsg.length(); i++) {
            if(BitFMsg.charAt(i) == '1' && btFd.charAt(i) == '0')
                pieces.add(i);
        }

        // checking to send accepted / Not accepted Message
        for(int i = 0; i < btFd.length(); i++) {
            if(BitFMsg.charAt(i) == '1' && BitFMsg.charAt(i) != btFd.charAt(i)) {
                System.out.println("Sending Interested Message");
                contInfo.send(new ActualMessage(2));
//                out.flush();
                return;
            }
        }
        System.out.println("Sending Not Interested Message");
        contInfo.send(new ActualMessage(3));
//        out.flush();
    }

    public void ReqmsgControl(ActualMessage actMsg) throws IOException {
        if(!peer.retrieveChokedKey().contains(contInfo.getPeerId())) {
            int reqPc = Integer.parseInt(actMsg.getMessagePayload());
            byte[] piece = peer.getPieceAtIndex().get(reqPc);
            contInfo.send(new PieceMessage(7, piece));
            peer. retrieveDownloadRt().put(contInfo.getPeerId(), peer. retrieveDownloadRt().getOrDefault(contInfo.getPeerId(), 0) + 1);
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
        for(String peerID : peer. retrieveP2PCon().keySet()) {
            ConnectionDetails contInfo = peer. retrieveP2PCon().get(peerID);
            String btFd = peer.retrieve_BM_Val().get(peerID);
            System.out.println("sending have to " + peerID +"having b_f" + btFd );
            contInfo.send(new ActualMessage(4, Integer.toString(pcIdx)));

        }

        if(peer.getPieceAtIndex().size() == Config.totPieces) {
            for(String peerID : peer. retrieveP2PCon().keySet()) {
                ConnectionDetails contInfo = peer. retrieveP2PCon().get(peerID);
                contInfo.send(new ActualMessage(3, null));
            }
            peer.log_Writer("At time:["+ Instant.now() + "]: Peer " + peer.getPid() +"  downloaded  complete file");
            return;
        }

        ConcurrentMap<String, String> map = peer.retrieve_BM_Val();
        String bitFieldOfReceivedPeer = map.get(contInfo.getPeerId());
        char[] myBitField = peer.retrieve_BF();
        boolean hasPcStr = false;
        for(int i = 0; i < bitFieldOfReceivedPeer.length(); i++) {
            if(myBitField[i] == '0' && bitFieldOfReceivedPeer.charAt(i) == '1') {
                hasPcStr = true;
                break;
            }
        }
        if(!hasPcStr)
            contInfo.send(new ActualMessage(3));
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class Peer {
    //Setting up peer detail keys
    Config config;
    String peerID;
    char[] b_f = new char[Config.totPieces];
    String hostName;
    int portnum;
    Srvercls svr;
    List<ConnectionDetails> connts = new ArrayList<>();
    List<peerData> totalPrs = new ArrayList<>();

    ConcurrentMap<String, String> BtFMap = new ConcurrentHashMap<>();

    List<peerData> connection_prs = new ArrayList<>();
    Set<String> accepted = Collections.synchronizedSet(new HashSet<>());
    Set<String> unCkd = Collections.synchronizedSet(new HashSet<>());

    Set<String> Ckd = Collections.synchronizedSet(new HashSet<>());
    ConcurrentMap<String, Integer> download_spd = new ConcurrentHashMap<>();
    ConcurrentMap<String, ConnectionDetails> prConnect = new ConcurrentHashMap<>();

    Set<String> prfNbr = Collections.synchronizedSet(new HashSet<>());
    Set<String> rstNeighbourselect = Collections.synchronizedSet(new HashSet<>());
    ConcurrentMap<Integer, byte[]> ele_index = new ConcurrentHashMap<>();

    PriorityBlockingQueue<node_slct> selectionNodes;
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

    public PriorityBlockingQueue<node_slct> SelectG_t() {
        //Retrieve blocking Queue list
        return selectionNodes;
    }

    public void Selects_t(PriorityBlockingQueue<node_slct> selectionNodes) {
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

    public void setConfig(Config config) {
        this.config = config;
    }

    public void setConnections(List<ConnectionDetails> connts) {
        this.connts = connts;
    }

    public void setAllPeers(List<peerData> totalPrs) {
        //Setting total peers
        this.totalPrs = totalPrs;
    }

    public void BitFields_t(ConcurrentMap<String, String> BtFMap) {
        //Mapping Bitfield values
        this.BtFMap = BtFMap;
    }

    public void setPeersToConnect(List<peerData> connection_prs) {
        this.connection_prs = connection_prs;
    }

    public void unChokeKeySetter(Set<String> unCkd) {
        //Setting Unchoked status of peer
        this.unCkd = unCkd;
    }

    public void  chokeKeySetter(Set<String> Ckd) {
        //Setting Choked status of peer
        this.Ckd = Ckd;
    }

    public void PeerToConnSetter(ConcurrentMap<String, ConnectionDetails> prConnect) {
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

    public ConcurrentMap<String, ConnectionDetails>  retrieveP2PCon() {
        return prConnect;
    }

    public ConcurrentMap<String, Integer>  retrieveDownloadRt() {
        return download_spd;
    }

    public Set<String>  unChokedKeySetter() {
        return unCkd;
    }

    public Set<String> retrieveChokedKey() {
        return Ckd;
    }

    volatile Queue<Elem_MQ> msgQ = new LinkedList<>();
    boolean verifySelectedNeighbours;
    MsgProcessor mssgController;
    Map<String, String> statusOfHS = new HashMap<>();
    Map<String, String> statusOfBF = new HashMap<>();

    public Map<String, String> retrieveBFVal() {
        return statusOfBF;
    }

    public void BFValSetter(Map<String, String> statusOfBF) {
        this.statusOfBF = statusOfBF;
    }

    public Map<String, String> retrieveHSVal() {
        return statusOfHS;
    }

    public void setHSVal(Map<String, String> statusOfHS) {
        this.statusOfHS = statusOfHS;
    }

    public MsgProcessor retrieveMH() {
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

    public Queue<Elem_MQ> retrieve_MQ() {
        return msgQ;
    }

    public void MQ_Setter(Queue<Elem_MQ> msgQ) {
        this.msgQ = msgQ;
    }

    public List<ConnectionDetails> retrieve_Conn() {
        //Retrieve Connection Details
        return connts;
    }

    public Set<String> retrieve_Interested_Key() {
        //Return Interested status
        return accepted;
    }

    public List<peerData> retrieve_P2Conn() {
        return connection_prs;
    }

    public Config retrieve_Config() {
        return config;
    }

    public Peer(String peerID, Config config) throws IOException {
        this.peerID = peerID;
        this.config = config;
        this.fw = new FileWriter("log_peer_[" + peerID +"].log");
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

    public List<peerData> retrieve_Peers() {
        //retrieve total peers
        return totalPrs;
    }

    public Srvercls retrieve_Srv() {
        //Retrieve svr value
        return svr;
    }

    public void Srv_Setter(Srvercls svr) {
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

    public int portnum_gt() {
        //Retrieving Port NUmber
        return portnum;
    }

    public void portnum_st(int portnum) {
        //Setting port number value
        this.portnum = portnum;
    }

    public String getPid() {
        //Retrieving Peer ID
        return peerID;
    }

    public void setPid(String peerID) {
        //Setting Peer ID
        this.peerID = peerID;
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
                peerData p_Det = new peerData(str_split[0], str_split[1], Integer.parseInt(str_split[2]), Integer.parseInt(str_split[3]));
                if(p_Det.getPid().compareTo(this.peerID) == 0) {
                    p_Det_Setter(p_Det);
                    is_P2P_Conn = false;
                }
                if(is_P2P_Conn) {
                    connection_prs.add(p_Det);
                }
                totalPrs.add(p_Det);
                Ckd.add(p_Det.getPid());
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

    public void p_Det_Setter(peerData p_Det) {
        //Setting Peer details
        char[] b_f = new char[Config.totPieces];
        if(p_Det.fileext_gt() == 1) {
            Arrays.fill(b_f, '1');
        }
        else {
            Arrays.fill(b_f, '0');
        }
        initFileExists_st(p_Det.fileext_gt() == 1);
        BF_Setter(b_f);
        portnum_st(p_Det.portnum_gt());
        HN_Setter(p_Det.getHost());
    }
}

//_______________________________________________________________________________________________________________________________________________________________

// Entry point

//main process of the peers


//_______________________________________________________________________________________________________________________________________________________________

class Elem_MQ {
    ConnectionDetails contInfo;
    Object message;

    public Elem_MQ(ConnectionDetails contInfo, Object message) {
        this.contInfo = contInfo;
        this.message = message;
    }

    public ConnectionDetails retrieve_Conn_Det() {
        return contInfo;
    }

    public void Con_Det_Setter(ConnectionDetails contInfo) {
        this.contInfo = contInfo;
    }

    public Object retrieve_Msg() {
        return message;
    }

    public void Msg_Setter(Object message) {
        this.message = message;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class ActualMessage implements Serializable {
    private int msg_size;
    private int messageType;
    private String messagePayload;
    private static final long serialVersionUID = 8983558202217591746L;

    public ActualMessage() {}

    public ActualMessage(int messageType, String messagePayload) {
        this.messageType = messageType;
        this.messagePayload = messagePayload;
        //@ Recieved Null pointer because of this
        if(messagePayload != null)
            this.msg_size = messagePayload.length() + 1;
        else
            this.msg_size = 1;
    }

    public ActualMessage(int messageType) {
        this.messageType = messageType;
        this.msg_size = 1;
    }

    public int MSG_len_gt() {
        //Retrieve Message Length
        return msg_size;
    }

    public void MSG_len_st(int msg_size) {
        //Update the Message size
        this.msg_size = msg_size;
    }

    public int getMessageType() {
        //Retrieve type of message
        return messageType;
    }

    public void MSG_ty_st(int messageType) {
        this.messageType = messageType;
    }

    public String getMessagePayload() {
        //Retrieving the Message Payload
        return messagePayload;
    }

    public void setMessagePayload(String messagePayload) {
        //Updating the Message payload
        this.messagePayload = messagePayload;
    }
}

//_______________________________________________________________________________________________________________________________________________________________

class Srvercls extends Thread{

    private static int sPort;
    Peer peer;

    public Srvercls(int portnum, Peer peer) {
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
                ObjectInputStream inreslt = new ObjectInputStream(socket.getInputStream());
                String peerIdConnected = Integer.toString(clientNum);
                ConnectionDetails contInfo = new ConnectionDetails(peerIdConnected, socket, out, inreslt, peer, new ConcurrentLinkedQueue<Object>());
                peer.retrieve_Conn().add(contInfo);

                contInfo.start();
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

