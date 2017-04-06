package network;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Random;

import resources.Logs;
import resources.Util;
import resources.Util.MessageType;
import peer.Chunk;
import peer.Peer;
import peer.Record;
import protocols.ChunkBackupProtocol;

public class MessageHandler extends Thread
{
	private Peer peer = null; //peer associado ao listener
	private Message msg = null;

	/**
	 * Parse the message to object Message
	 * @param peer
	 * @param msg
	 */
	public MessageHandler(Peer peer,byte[] msg)
	{
		this.peer = peer;
		this.msg = parseMessage(msg);
	}

	/**
	 * Execute actions depending on Message Type
	 */
	public void run() 
	{		
		//Don't process messages that were sent by himself
		if(peer.getID() != msg.getSenderId())
		{	
			Logs.receivedMessageLog(this.msg);

			switch (msg.getType()) {
			case PUTCHUNK:
				peer.getMessageRecord().addPutchunkMessage(msg.getFileId(), msg.getChunkNo());
				handlePutchunk(msg.getFileId(),msg.getChunkNo(),msg.getReplicationDeg(),msg.getBody());
				break;
			case STORED:
				peer.getMessageRecord().addStoredMessage(msg.getFileId(), msg.getChunkNo(), msg.getSenderId());
				handleStore(msg.getFileId(), msg.getChunkNo(),msg.getSenderId());	
				break;
			case GETCHUNK:
				handleGetchunk(msg.getFileId(),msg.getChunkNo());
				break;
			case CHUNK:
				//peer.getMessageRecord().addChunkMessage(msg.getFileId(), msg.getChunkNo());
				handleChunk(msg.getFileId(), msg.getChunkNo(), msg.getBody());
				break;
			case DELETE:
				handleDelete(msg.getFileId());
				break;
			case REMOVED:
				handleRemoved(msg.getFileId(),msg.getChunkNo(),msg.getSenderId());
				break;
			default:
				break;
			}
		}
	}

	/**
	 * Random Delay
	 */
	public void randomDelay(){
		Random delay = new Random();
		try {
			Thread.sleep(delay.nextInt(Util.RND_DELAY));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Peer response to other peer PUTCHUNK message
	 * @param c
	 */
	private synchronized void handlePutchunk(String fileId, int chunkNo,int repDeg,byte[] body){
		Chunk c = new Chunk(fileId, chunkNo, body);

		//response message : STORED
		Message msg = new Message(Util.MessageType.STORED,peer.getVersion(),peer.getID(),c.getFileId(),c.getChunkNo());

		//verifies chunk existence in this peer
		//boolean alreadyExists = peer.fileManager.chunkExists(c.getFileId(),c.getChunkNo());
		boolean alreadyExists = peer.getMulticastRecord().hasOnMyChunk(fileId, chunkNo);

		//no space available and file does not exist -> can't store
		if(!peer.fileManager.hasSpaceAvailable(c) && !alreadyExists)
			return;
		else
		{
			if(alreadyExists)				//warns immediatly
			{
				peer.getMc().send(msg);
				Logs.sentMessageLog(msg);
			}
			else
			{
				//waiting time
				randomDelay();
				int rep = 0;
				//Get replication degree recorded before the peer processed the store
				ArrayList<Integer> peersWithChunk = peer.getMessageRecord().getPeersWithChunk(fileId, chunkNo);

				if(peersWithChunk != null)
					rep = peersWithChunk.size();

				//If the replication degree is lower thatn the desired
				//if(rep < repDeg){							//--> Enhancement*/
					//send STORED message
					peer.getMc().send(msg);
					Logs.sentMessageLog(msg);

					//Save info on my Chunks 
					peer.getMulticastRecord().addToMyChunks(fileId, chunkNo, repDeg);
					//Update Actual Repetition Degree
					peer.getMulticastRecord().setPeersOnMyChunk(fileId, chunkNo, peersWithChunk);
					peer.getMulticastRecord().addPeerOnMyChunk(fileId, chunkNo, peer.getID());

					//System.out.println("CHUNK " + chunkNo + " REPLICATION: " + (int)(rep+1) + " DESIRED: " + repDeg);
					peer.fileManager.saveChunk(c);
				/*}
				else
					peer.getMessageRecord().removeStoredMessages(fileId, chunkNo);	//only keeps the ones refered to his backupChunks --> Enhancement
			*/
			}


		}
	}

	/**
	 * Peer response to other peer STORE message
	 */
	private synchronized void handleStore(String fileId, int chunkNo, int senderId){
		//Updates the Repetition Degree if the peer has the chunk
		peer.getMulticastRecord().addPeerOnMyChunk(fileId,chunkNo,senderId);

		Chunk c = peer.getMulticastRecord().getMyChunk(fileId, chunkNo);

		if(c != null)
			System.out.println("CHUNK " + chunkNo + " REPDEG: " + c.getAtualRepDeg());

		//Record the storedChunks in case the peer is the owner of the backup file
		peer.getMulticastRecord().recordStoreChunks(fileId, chunkNo, senderId);
	}	

	/**
	 * Peer response to other peer GETCHUNK message
	 */
	private synchronized void handleGetchunk(String fileId, int chunkNo){
		
		//peer has chunk stored
		if(peer.record.hasOnMyChunk(fileId, chunkNo))
		{
			//body
			byte[] body = peer.fileManager.getChunkContent(fileId, chunkNo);
			//create CHUNK message
			Message msg = new Message(Util.MessageType.CHUNK,peer.getVersion(),peer.getID(),fileId,chunkNo,body);
			
			//wait 0-400 ms
			randomDelay();
			
			//chunk still needed by the initiator peer
			if(!peer.getMessageRecord().receivedChunkMessage(fileId, chunkNo))
			{
				peer.getMdr().send(msg);
				Logs.sentMessageLog(msg);
			}
		}
		//peer.getMessageRecord().removeChunkMessages(fileId, chunkNo);
	}

	/**
	 * Peer response to other peer CHUNK message
	 */
	private synchronized void handleChunk(String fileId, int chunkNo, byte[] body){
		//chunk message received by intiator peer 
		if(peer.getMulticastRecord().checkRestore(fileId))
		{
			//chunk restored, yey
			if(peer.getMulticastRecord().recordRestoreChunks(fileId,chunkNo,body))
				Logs.chunkRestored(chunkNo);
		}

		//save history of chunks at mdr (chunkNo, fileId)
		peer.msgRecord.addChunkMessage(fileId, chunkNo);
	}

	/**
	 * Peer response to other peer DELETE message
	 */
	private synchronized void handleDelete(String fileId){
		if(peer.getMulticastRecord().myChunksBelongsToFile(fileId))
		{
			peer.fileManager.deleteChunks(fileId);				//remove from disk
			peer.getMulticastRecord().removeFromMyChunks(fileId);		//remove from record
		}
	}

	/**
	 * Peer response to other peer REMOVED message
	 */
	private synchronized void handleRemoved(String fileId, int chunkNo, int peerNo){

		Record record = peer.getMulticastRecord();
		String filename = record.getStoredFilename(fileId);	//from stored

		if(filename == null)
			return;

		byte[] data = null;
		int repDegree = 0;
		int desiredRepDegree = 0;
		
		//Owner
		if(record.checkStored(fileId, chunkNo) != null){			
			System.out.println("OWNER");

			//Update stored record
			record.deleteStored(fileId, chunkNo, peerNo);
			
			desiredRepDegree = record.getStoredReplicationDegree(fileId);
			
			ArrayList<Integer> peersWithChunk = record.checkStored(fileId, chunkNo);	//Actual replication degree
			if(peersWithChunk !=null)
				repDegree = peersWithChunk.size();

			if(repDegree<desiredRepDegree){

				ArrayList<Chunk> chunks = peer.fileManager.splitFileInChunks(Util.PEERS_DIR + "Peer" + peer.getID() + Util.RESTORES_DIR + filename);
				if(chunks.size() < chunkNo){
					System.out.println("Ficheiro n�o foi recuperado totalmente");
					return;
				}

				Chunk c = chunks.get(chunkNo);
				data = c.getData();
			}
		}
		//Not Owner but it has ths chunk on his backup diretory
		else if(peer.getMulticastRecord().hasOnMyChunk(fileId, chunkNo)){
			System.out.println("NOT OWNER");
			peer.getMulticastRecord().remPeerWithMyChunk(fileId, chunkNo, peerNo);
			data = peer.fileManager.getChunkContent(fileId, chunkNo);
			repDegree = peer.getMulticastRecord().getMyChunk(fileId, chunkNo).getAtualRepDeg();
			desiredRepDegree = peer.getMulticastRecord().getMyChunk(fileId, chunkNo).getReplicationDeg();
		}
		
		/*
		 * If replicaiton degree is bellow desired it will start the chunkbackup protocol
		 * only if after a random time it doesn't received any putchunk for the same fileId and chunkNo
		 */
		if(repDegree < desiredRepDegree){
			peer.getMessageRecord().removePutChunkMessages(fileId, chunkNo);	//reset recording
			peer.getMessageRecord().startRecordingPutchunks(fileId, chunkNo);	//start record
			
			randomDelay();

			if(!peer.getMessageRecord().receivedPutchunkMessage(fileId, chunkNo)){
				Message msg = new Message(MessageType.PUTCHUNK,peer.getVersion(),peer.getID(),fileId,chunkNo,repDegree,data);
				Logs.sentMessageLog(msg);
				new ChunkBackupProtocol(peer.getMdb(), record, msg).start();
			}
		}

	}

	/*
	 * Preenche os atributos da classe com os respetivos valores 
	 */
	private Message parseMessage(byte[] message)
	{
		Message parsed = null;

		ByteArrayInputStream stream = new ByteArrayInputStream(message);
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

		try
		{
			String header = reader.readLine();	//a primeira linha corresponde a header

			//interpreta��o da header
			String[] parts = header.split("\\s");

			Util.MessageType type_rcv = validateMessageType(parts[0]);

			//common
			char[] version_rcv = validateVersion(parts[1]);
			int senderId_rcv = Integer.parseInt(parts[2]);
			String fileId_rcv = parts[3];

			//all except delete
			int chunkNo_rcv = -1;
			if(type_rcv.compareTo(Util.MessageType.DELETE) != 0)
				chunkNo_rcv = Integer.parseInt(parts[4]);

			//just putchunk
			int replicationDeg_rcv = -1;
			if(type_rcv.compareTo(Util.MessageType.PUTCHUNK) == 0){
				replicationDeg_rcv = Integer.parseInt(parts[5]);
			}

			//Removes the last sequences of white spaces (\s) and null characters (\0)
			//String msg_received = (new String(packet.getData()).replaceAll("[\0 \\s]*$", ""));
			//temporario?
			int offset = header.length() + Message.LINE_SEPARATOR.length()*2;
			byte[] body = new byte[64000];
			System.arraycopy(message, offset, body, 0, 64000);

			//create messages
			if(type_rcv.compareTo(Util.MessageType.DELETE) == 0)
				parsed = new Message(type_rcv,version_rcv,senderId_rcv,fileId_rcv);	
			else if(type_rcv.compareTo(Util.MessageType.GETCHUNK) == 0 || type_rcv.compareTo(Util.MessageType.STORED) == 0 || type_rcv.compareTo(Util.MessageType.REMOVED) == 0)
				parsed = new Message(type_rcv,version_rcv,senderId_rcv,fileId_rcv,chunkNo_rcv) ;	
			else if(type_rcv.compareTo(Util.MessageType.PUTCHUNK) == 0)
				parsed = new Message(type_rcv,version_rcv,senderId_rcv,fileId_rcv,chunkNo_rcv,replicationDeg_rcv,body);
			else if(type_rcv.compareTo(Util.MessageType.CHUNK) == 0)
				parsed = new Message(type_rcv,version_rcv,senderId_rcv,fileId_rcv,chunkNo_rcv,body);

			reader.close();
			stream.close();
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return parsed;
	}

	private char[] validateVersion(String string) 
	{
		char[] vs = string.toCharArray();
		char[] peerVersion = peer.getVersion();
		if(vs[0] == peerVersion[0] && vs[1] == peerVersion[1])
			return vs;

		return null;	//deve retornar um erro
	}

	private Util.MessageType validateMessageType(String string) 
	{
		//nao sei se ha restricoes aqui
		return Util.MessageType.valueOf(string);
	}

	public Peer getPeer() {
		return peer;
	}

	public void setPeer(Peer peer) {
		this.peer = peer;
	}

}
