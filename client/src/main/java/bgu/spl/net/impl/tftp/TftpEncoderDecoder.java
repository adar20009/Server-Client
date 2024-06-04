package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;


enum Opcode {
    NONE, RRQ, WRQ, DATA, ACK, ERROR, DIRQ, LOGRQ, DELRQ, BCAST, DISC;

    public static Opcode convertToOpcode(short opcode) {
        switch (opcode) {
            case 1:
                return RRQ;
            case 2:
                return WRQ;
            case 3:
                return DATA;
            case 4:
                return ACK;
            case 5:
                return ERROR;
            case 6:
                return DIRQ;
            case 7:
                return LOGRQ;
            case 8:
                return DELRQ;
            case 9:
                return BCAST;
            case 10:
                return DISC;
            default:
                return NONE;
        }
    }
}


public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    private byte[] bytes = new byte[0];
    private Opcode opCode = Opcode.NONE;
    private long optExpectedLen = Integer.MAX_VALUE;


     public byte[] poolBytes() {
        byte[] message = this.bytes.clone();
        this.bytes = new byte[0];
        this.setOpcode(Opcode.NONE);
        return message;
    }

    private void setOpcode(Opcode opCode) {
        this.opCode = opCode;
        switch (opCode) {
            case NONE:
                this.optExpectedLen = Integer.MAX_VALUE;
                break;
            case RRQ:
            case WRQ:
            case DIRQ:
            case LOGRQ:
            case DELRQ:
            case DISC:
                this.optExpectedLen = 2;
                break;
            case BCAST:
                this.optExpectedLen = 3;
                break;
            case ACK:
            case ERROR:
                this.optExpectedLen = 4;
                break;
            case DATA:
                this.optExpectedLen = 6;
                break;
        }
    }

    private Opcode peekOpcode() {
        assert this.bytes.length >= 2;
        short shortOpCode = (short) (((short) bytes[0]) << 8 | (short) (bytes[1]) & 0xff);
        return Opcode.convertToOpcode(shortOpCode);
    }

    private boolean shouldHaveZero(Opcode opCode) {
        switch (opCode) {
            case RRQ:
            case WRQ:
            case ERROR:
            case BCAST:
            case LOGRQ:
            case DELRQ:
            case NONE:
                return true;
            default:
                return false;
        }
    }


    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (this.bytes.length >= this.optExpectedLen && nextByte == 0x0) {
            byte[] message = this.poolBytes();
            this.setOpcode(Opcode.NONE);
            return message;
        } else {
            byte[] temp = new byte[this.bytes.length + 1];
            System.arraycopy(this.bytes, 0, temp, 0, this.bytes.length);
            temp[this.bytes.length] = nextByte;
            this.bytes = temp;
            if (this.bytes.length == 2) {
                this.setOpcode(this.peekOpcode());
            } 
            if (this.opCode == Opcode.DATA && this.bytes.length == 4) {
                int size = ((this.bytes[2] & 0xFF) << 8) | (this.bytes[3] & 0xFF);
                this.optExpectedLen = 6 + size;
            }
            if (!this.shouldHaveZero(this.opCode) && this.bytes.length == this.optExpectedLen) {
                byte[] message = this.poolBytes();
                this.setOpcode(Opcode.NONE);
                return message;
            }
            return null;
        }
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }
}