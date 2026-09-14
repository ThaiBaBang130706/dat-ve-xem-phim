package vn.cinema.common;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
public final class JsonLineCodec {
 public static final int MAX_FRAME_BYTES=1_048_576;
 private JsonLineCodec() {}
 public static String read(InputStream in) throws IOException {
  ByteArrayOutputStream b=new ByteArrayOutputStream();
  while(true) {
   int ch=in.read();
   if(ch==-1) {if(b.size()==0)return null;throw new EOFException("Khung JSON chưa có ký tự xuống dòng");}
   if(ch=='\n')break;
   if(b.size()>=MAX_FRAME_BYTES)throw new IOException("Khung JSON quá lớn");
   b.write(ch);
  }
  return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(b.toByteArray())).toString();
 }
 public static void write(OutputStream out,Object message) throws IOException {
  byte[] b=Json.GSON.toJson(message).getBytes(StandardCharsets.UTF_8);
  if(b.length>MAX_FRAME_BYTES)throw new IOException("Khung JSON quá lớn");
  out.write(b);out.write('\n');out.flush();
 }
}
