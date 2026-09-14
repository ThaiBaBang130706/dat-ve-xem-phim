package vn.cinema.common;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JsonLineCodecTest {
 @Test void readsVietnameseAndMultipleFrames()throws Exception {
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  JsonLineCodec.write(out,Json.obj("message","Ghế đã bán"));
  JsonLineCodec.write(out,Json.obj("type","PING"));
  InputStream fragmented=new FilterInputStream(new ByteArrayInputStream(out.toByteArray())){
   @Override public int read(byte[] b,int off,int len)throws IOException{return super.read(b,off,Math.min(1,len));}
  };
  assertEquals("Ghế đã bán",Json.GSON.fromJson(JsonLineCodec.read(fragmented),com.google.gson.JsonObject.class).get("message").getAsString());
  assertTrue(JsonLineCodec.read(fragmented).contains("PING"));
  assertNull(JsonLineCodec.read(fragmented));
 }
 @Test void rejectsTruncatedOversizedAndInvalidUtf8(){
  assertThrows(EOFException.class,()->JsonLineCodec.read(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))));
  byte[] oversized=new byte[JsonLineCodec.MAX_FRAME_BYTES+1];java.util.Arrays.fill(oversized,(byte)'x');
  assertThrows(IOException.class,()->JsonLineCodec.read(new ByteArrayInputStream(oversized)));
  assertThrows(IOException.class,()->JsonLineCodec.read(new ByteArrayInputStream(new byte[]{(byte)0xc3,0x28,10})));
 }
}
