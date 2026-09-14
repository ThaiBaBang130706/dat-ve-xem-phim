package vn.cinema.client;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class FxmlTest {
 @BeforeAll static void start()throws Exception {
  CountDownLatch ready=new CountDownLatch(1);Platform.startup(()->{Platform.setImplicitExit(false);ready.countDown();});
  assertTrue(ready.await(10,TimeUnit.SECONDS));
 }
 @Test void allScreensAndStylesLoadOnJavaFxThread()throws Exception {
  CompletableFuture<Void> done=new CompletableFuture<>();
  Platform.runLater(()->{
   try{
    for(String file:new String[]{"login.fxml","main.fxml","admin.fxml"}){
     Parent root=FXMLLoader.load(getClass().getResource("/vn/cinema/client/"+file));
     Scene scene=new Scene(root,1180,780);scene.getStylesheets().add(getClass().getResource("/vn/cinema/client/styles.css").toExternalForm());
     root.applyCss();root.layout();assertNotNull(root);
    }
    done.complete(null);
   }catch(Throwable e){done.completeExceptionally(e);}
  });
  done.get(20,TimeUnit.SECONDS);
 }
 @AfterAll static void stop(){Platform.exit();}
}
