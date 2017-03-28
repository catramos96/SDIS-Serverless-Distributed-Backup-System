package initiators;

import java.util.ArrayList;
import java.util.HashMap;

import network.Message;
import peer.Peer;
import protocols.SpaceReclaimingProtocol;
import resources.Util.MessageType;

public class ReclaimTrigger extends Thread{
	protected Peer peer = null;
	protected int spaceToReclaim = 0;
	/**
	 * Peer initiator response to client request for RECLAIM
	 * @param action
	 * @param filename
	 * @param replicationDegree
	 */
	public ReclaimTrigger(Peer peer, int spaceToReclaim){
		this.peer = peer;
		this.spaceToReclaim = spaceToReclaim;
	}
	
	public void run(){
		
		//Calcula a mem�ria necess�ria a libertar
		int memoryToRelease = peer.fileManager.memoryToRelease(spaceToReclaim);
		
		System.out.println("SPACE: " + spaceToReclaim);
		System.out.println("RELEASE MEMORY: " + memoryToRelease);
		
		if(memoryToRelease > 0){
			
			//elimina os ficheiros conforme o espa�o necess�rio
			ArrayList<String> backupChunks = peer.fileManager.deleteNecessaryChunks(spaceToReclaim);
			
			for(String chunk : backupChunks){
				
				Integer chunkNo = Integer.parseInt(chunk.substring(0,1));
				String fileId = chunk.substring(1,chunk.length());	
				
				Message msg = new Message(MessageType.REMOVED,peer.getVersion(),peer.getID(),fileId,chunkNo);
				
				peer.getMc().send(msg);
				
			}
		
		}
		peer.setMessage("reclaim");
	}

}
