package ar.goro.ai.core;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
final class DiagnosticWavFixture {
 static byte[] wav(int rate,int channels,short[] samples){
  ByteBuffer b=ByteBuffer.allocate(44+samples.length*2).order(ByteOrder.LITTLE_ENDIAN);
  b.put(new byte[]{'R','I','F','F'}).putInt(36+samples.length*2).put(new byte[]{'W','A','V','E'});
  b.put(new byte[]{'f','m','t',' '}).putInt(16).putShort((short)1).putShort((short)channels);
  b.putInt(rate).putInt(rate*channels*2).putShort((short)(channels*2)).putShort((short)16);
  b.put(new byte[]{'d','a','t','a'}).putInt(samples.length*2);
  for(short value:samples)b.putShort(value);
  return b.array();
 }
 static short value(byte[] pcm,int frame){return (short)((pcm[frame*2]&255)|((pcm[frame*2+1]&255)<<8));}
 static byte[] shortSpeech(){return wav(16000,1,new short[3200]);}
}
