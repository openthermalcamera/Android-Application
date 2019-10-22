package com.themarpe.openthermalcamera;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

class Protocol {

	//two interfaces:
	//1 for sending data
	//1 for receiving new responses
	interface ISender{
		void sendBytes(byte[] bytesToSend);
	}
	ISender sender = null;

	interface IResponseListener {
		void onResponse(Queue<Protocol.RspStruct> q);
	}
	IResponseListener responseListener = null;


	public Protocol(ISender sender, IResponseListener responseListener){
		this.sender = sender;
		this.responseListener = responseListener;
	}


	public static final int COMMAND_HEADER_SIZE = 3;
	public static final int NO_COMMAND = 0xFF;

	//commandCode
	public static final int CMD_PING = 0x00;
	public static final int CMD_DUMP_EE = 0x01;
	public static final int CMD_GET_FRAME_DATA = 0x02;
	public static final int CMD_SET_RESOLUTION = 0x03;
	public static final int CMD_GET_CUR_RESOLUTION = 0x04;
	public static final int CMD_SET_REFRESH_RATE = 0x05;
	public static final int CMD_GET_REFRESH_RATE = 0x06;
	public static final int CMD_SET_MODE = 0x07;
	public static final int CMD_GET_CUR_MODE = 0x08;
	public static final int CMD_SET_AUTO_FRAME_DATA_SENDING = 0x09;
    public static final int CMD_GET_FIRMWARE_VERSION = 0x0A;
	public static final int CMD_JUMP_TO_BOOTLOADER = 0x0B;



	public static class CmdStruct{
		int commandCode;
		int dataLength;
		ArrayList<Integer> data = new ArrayList<>();
	}


	public static final int RESPONSE_HEADER_SIZE = 4;
	public static final int NO_RESPONSE = 0xFF;

	//responseCode
	public static final int RSP_PING = 0x00;
	public static final int RSP_DUMP_EE = 0x01;
	public static final int RSP_GET_FRAME_DATA = 0x02;
	public static final int RSP_SET_RESOLUTION = 0x03;
	public static final int RSP_GET_CUR_RESOLUTION = 0x04;
	public static final int RSP_SET_REFRESH_RATE = 0x05;
	public static final int RSP_GET_REFRESH_RATE = 0x06;
	public static final int RSP_SET_MODE = 0x07;
	public static final int RSP_GET_CUR_MODE = 0x08;
	public static final int RSP_SET_AUTO_FRAME_DATA_SENDING = 0x09;
    public static final int RSP_GET_FIRMWARE_VERSION = 0x0A;
	public static final int RSP_JUMP_TO_BOOTLOADER = 0x0B;

	//dataCode
	public static final int CODE_OK = 0;
	public static final int CODE_NACK = -1;
	public static final int CODE_WRITTEN_VALUE_NOT_SAME = -2;
	public static final int CODE_I2C_FREQ_TOO_LOW = -8;

	public static class RspStruct{
		int responseCode;
		int dataCode;
		int dataLength;
		ArrayList<Integer> data = new ArrayList<>();
	}


	//Refresh rates
    public enum RefreshRate {
        HZ_MIN (0),
        HZ_1 (1),
        HZ_2 (2),
        HZ_4 (3),
        HZ_8 (4),
        HZ_16 (5),
        HZ_32 (6),
        HZ_64 (7);

        private final int rate;
        RefreshRate(int rate) { this.rate = rate; }

        public int getValue() { return rate; }
    }

    //Resolution
	public enum Resolution {
		BIT_16 (0),
		BIT_17 (1),
		BIT_18 (2),
		BIT_19 (3);

		private final int resolution;
		Resolution(int resolution) { this.resolution = resolution; }
		public int getValue() { return resolution; }
	}

    //FirmwareVersion structure
    public static class FirmwareVersion {
	    int major;
	    int minor;
	    int revision;

	    public static FirmwareVersion parse(ArrayList<Integer> arr){
	        FirmwareVersion version = new FirmwareVersion();

	        if(arr.size() < 12) return null;
	        version.major = arr.get(0) << 24 | arr.get(1) << 16 | arr.get(2) << 8 | arr.get(3);
            version.minor = arr.get(4) << 24 | arr.get(5) << 16 | arr.get(6) << 8 | arr.get(7);
            version.revision = arr.get(8) << 24 | arr.get(9) << 16 | arr.get(10) << 8 | arr.get(11);
            return version;
        }

	    public String toString(){
	        return major + "." + minor + "." + revision;
        }
    }

	public enum ScanMode {
		CHESS (1),
		INTERLEAVED (0);

		private final int mode;
		ScanMode(int mode) { this.mode = mode; }
		public int getValue() { return mode; }
	}


	Queue<Byte> messageBuffer = new LinkedList<Byte>();

	Queue<Protocol.RspStruct> responseQueue = new LinkedList<Protocol.RspStruct>();


	public void handleNewData(byte[] data){

		//received a chunk of message. store in message buffer
		for(int i = 0; i < data.length; i++ ){
			messageBuffer.add(data[i]);
		}

		//count number of messages
		int numDelimiters = 0;
		for(Byte b : messageBuffer){
			//if current byte is a message delimiter (0x00), increase counter
			if(b == 0x00){
				numDelimiters++;
			}
		}

		//extract messages
		for(int i = 0; i < numDelimiters; i++){
			//Add data to array
			ArrayList<Character> arrayOfEncoded = new ArrayList<Character>();
			byte b;
			while(  messageBuffer.size() > 0 && (b = messageBuffer.poll()) != 0x00){
				char c = (char) (((char) b) & 0xFF);
				arrayOfEncoded.add(c);
			}

			char[] cobsEncoded = new char[arrayOfEncoded.size()];
			for(int k = 0; k< arrayOfEncoded.size(); k++){
				cobsEncoded[k] = arrayOfEncoded.get(k);
			}

			char[] cobsDecoded = new char[Cobs.decodeDstBufMaxLen(cobsEncoded.length)];

			//decode message
			Cobs.DecodeResult decodeResult = Cobs.decode(cobsDecoded, cobsDecoded.length, cobsEncoded, cobsEncoded.length);

			//create empty response
			Protocol.RspStruct response = new Protocol.RspStruct();
			//set to no response
			response.responseCode = Protocol.NO_RESPONSE;


			//check size
			if(decodeResult.outLen >= Protocol.RESPONSE_HEADER_SIZE){
				//size ok

				response.responseCode = ((int) cobsDecoded[0]) & 0xFF;
				response.dataCode = ((int) cobsDecoded[1]) & 0xFF;
				response.dataLength = (( ( (int) cobsDecoded[2]) & 0xFF) << 8) | ( ((int) cobsDecoded[3]) & 0xFF);

				//check data length
				if(response.dataLength == decodeResult.outLen - Protocol.RESPONSE_HEADER_SIZE){

					// data length ok, copy data to response structure
					for(int f = 0; f < response.dataLength; f++){
						int counter = Protocol.RESPONSE_HEADER_SIZE + f;
						response.data.add(((int) cobsDecoded[counter]) & 0xFF);
					}

					//response successfuly created, add it to queue
					responseQueue.add(response);

					//notify
					if(responseListener != null) responseListener.onResponse(responseQueue);

				}


			}


		}
	}



	public void sendCommand(Protocol.CmdStruct cmd){
		//prepare to send command
		//create char array

		if(cmd.dataLength != cmd.data.size()) return;

		char[] message = new char[Protocol.COMMAND_HEADER_SIZE + cmd.dataLength];
		message[0] = (char) (cmd.commandCode);
		message[1] = (char) ((cmd.dataLength >> 8) & 0xFF);
		message[2] = (char) (cmd.dataLength & 0xFF);

		for(int i = 0; i<cmd.dataLength; i++){
			message[i + Protocol.COMMAND_HEADER_SIZE] = (char) (cmd.data.get(i) & 0xFF);
		}

		//prepare to encode message with COBS
		//create dst buffer
		char[] encodedMessage = new char[Cobs.encodeDstBufMaxLen(message.length)];
		Cobs.EncodeResult encodeResult = Cobs.encode(encodedMessage, encodedMessage.length, message, message.length);

		if(encodeResult.status == Cobs.EncodeStatus.OK){
			//data encoded successfully

			//create a byte array with length of encoded message and additional byte for delimiter (0x00)
			byte[] toWrite = new byte[encodeResult.outLen + 1];

			//copy data to byte array for transmittion over usb
			for(int i = 0; i< encodeResult.outLen; i++){
				toWrite[i] = (byte) (encodedMessage[i] & 0xFF);
			}

			//add message delimiter
			toWrite[encodeResult.outLen] = 0x00;

			//send
			if (sender != null) {
				sender.sendBytes(toWrite);
			}

		}

	}






}