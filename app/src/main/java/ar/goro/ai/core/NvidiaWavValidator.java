package ar.goro.ai.core;

/**
 * Validates the actual RIFF/WAVE payload returned by NVIDIA Magpie.
 * Header-only checks are insufficient: an empty or truncated WAV can otherwise
 * make GORO claim it has spoken without producing any sound.
 */
public final class NvidiaWavValidator {
 private NvidiaWavValidator(){}
 public static final class Info {
  public final int sampleRate,channels,bitsPerSample,byteRate,dataOffset,dataLength;
  public final long durationMs;
  private Info(int sampleRate,int channels,int bitsPerSample,int byteRate,int dataOffset,int dataLength){
   this.sampleRate=sampleRate;this.channels=channels;this.bitsPerSample=bitsPerSample;
   this.byteRate=byteRate;this.dataOffset=dataOffset;this.dataLength=dataLength;
   this.durationMs=1000L*dataLength/byteRate;
  }
 }
 private static int le16(byte[] data,int i){
  return (data[i]&255)|((data[i+1]&255)<<8);
 }
 private static long le32(byte[] data,int i){
  return ((long)data[i]&255)|(((long)data[i+1]&255)<<8)|
    (((long)data[i+2]&255)<<16)|(((long)data[i+3]&255)<<24);
 }
 private static boolean id(byte[] data,int start,String name){
  if(data.length-start<4)return false;
  for(int i=0;i<4;i++)if(data[start+i]!=(byte)name.charAt(i))return false;
  return true;
 }
 public static Info inspect(byte[] wav){
  if(wav==null||wav.length<44||!id(wav,0,"RIFF")||!id(wav,8,"WAVE"))
   throw new IllegalArgumentException("La respuesta de voz no es WAV RIFF");
  long declared=le32(wav,4);
  if(declared<36||declared+8>wav.length)
   throw new IllegalArgumentException("WAV truncado");
  int channels=0, sampleRate=0, bits=0, byteRate=0, blockAlign=0, format=0;
  int dataOffset=-1,dataLength=0;
  for(int p=12;p+8<=wav.length;){
   long size=le32(wav,p+4);
   if(size>wav.length-p-8)throw new IllegalArgumentException("Chunk WAV incompleto");
   if(id(wav,p,"fmt ")){
    if(size<16)throw new IllegalArgumentException("WAV fmt inválido");
    format=le16(wav,p+8);channels=le16(wav,p+10);
    sampleRate=(int)le32(wav,p+12);byteRate=(int)le32(wav,p+16);
    blockAlign=le16(wav,p+20);bits=le16(wav,p+22);
   }
   if(id(wav,p,"data")){
    dataOffset=p+8;dataLength=(int)size;
   }
   p+=(int)(8+size+(size&1));
  }
  if(format!=1||channels<1||channels>2||bits!=16||
    sampleRate<8000||sampleRate>96000||
    byteRate!=sampleRate*channels*2||blockAlign!=channels*2)
   throw new IllegalArgumentException("WAV PCM16 no compatible");
  if(dataOffset<0||dataLength<blockAlign||dataLength%blockAlign!=0)
   throw new IllegalArgumentException("WAV sin muestras de voz válidas");
  return new Info(sampleRate,channels,bits,byteRate,dataOffset,dataLength);
 }
}
