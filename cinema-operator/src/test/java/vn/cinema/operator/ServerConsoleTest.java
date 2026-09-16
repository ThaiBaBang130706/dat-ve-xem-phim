package vn.cinema.operator;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerConsoleTest {
 @BeforeAll static void setup()throws Exception{CountDownLatch latch=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);latch.countDown();});assertTrue(latch.await(10,TimeUnit.SECONDS));}
 @Test void operatorStartsAnActualTcpListener()throws Exception{
  Path path=Files.createTempDirectory("operator-test-").resolve("cinema.db");int tcp=port(),http=port();while(http==tcp)http=port();final int hp=http;
  ServerConsoleApp app=new ServerConsoleApp();Stage stage=fx(()->new Stage());
  try{
   fx(()->{app.start(stage);((TextField)stage.getScene().lookup("#tcpPort")).setText(""+tcp);((TextField)stage.getScene().lookup("#httpPort")).setText(""+hp);((TextField)stage.getScene().lookup("#database")).setText(path.toString());((Button)stage.getScene().lookup("#startServer")).fire();return null;});
   long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);boolean ready=false;
   while(System.nanoTime()<until){ready=fx(()->!((Button)stage.getScene().lookup("#stopServer")).isDisabled());if(ready)break;Thread.sleep(50);}assertTrue(ready,"Server GUI phải khởi động được");
   try(Socket socket=new Socket("127.0.0.1",tcp)){socket.setSoTimeout(3000);assertTrue(vn.cinema.common.JsonLineCodec.read(socket.getInputStream()).contains("EVENT_SYSTEM"));}
   fx(()->{stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();var image=stage.getScene().getRoot().snapshot(null,null);var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));Path folder=Path.of("target/ui-preview");Files.createDirectories(folder);javax.imageio.ImageIO.write(output,"png",folder.resolve("server.png").toFile());return null;});
  }finally{fx(()->{app.stop();stage.hide();return null;});}
  try(ServerSocket released=new ServerSocket(tcp)){assertEquals(tcp,released.getLocalPort());}
 }
 private static int port()throws Exception{try(ServerSocket s=new ServerSocket(0)){return s.getLocalPort();}}
 private static <T>T fx(Callable<T> c)throws Exception{CompletableFuture<T> f=new CompletableFuture<>();Platform.runLater(()->{try{f.complete(c.call());}catch(Throwable e){f.completeExceptionally(e);}});return f.get(20,TimeUnit.SECONDS);}
 @AfterAll static void end(){Platform.exit();}
}
