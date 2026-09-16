package vn.cinema.client;

import javafx.geometry.*;
import javafx.scene.canvas.*;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.*;

/** Original vector artwork; works offline and does not download external posters. */
public final class PosterArt {
 private PosterArt(){}
 public static StackPane create(long id,String title,double width,double height){
  Canvas canvas=new Canvas(width,height);GraphicsContext g=canvas.getGraphicsContext2D();
  String[] palette={"#75694B","#96734B","#446E7C","#765064"};Color accent=Color.web(palette[Math.floorMod(id,4)]);
  g.setFill(new LinearGradient(0,0,1,1,true,CycleMethod.NO_CYCLE,new Stop(0,Color.web("#101A25")),new Stop(.52,accent),new Stop(1,Color.web("#101014"))));g.fillRect(0,0,width,height);
  g.setFill(Color.web("#F4D69C",.7));g.fillOval(width*.53,height*.16,width*.26,width*.26);
  for(int i=0;i<35;i++){g.setFill(Color.web("#F4F1EA",.4));g.fillOval((i*47+13)%width,(i*23+5)%(height*.5),1.2,1.2);}
  if(id%2==0){
   g.setFill(Color.web("#233238"));g.fillPolygon(new double[]{0,width*.3,width*.52,width*.78,width,width,0},new double[]{height*.58,height*.33,height*.62,height*.42,height*.64,height,height},7);
   g.setFill(Color.web("#142229"));g.fillPolygon(new double[]{0,width*.35,width*.72,width,width,0},new double[]{height*.72,height*.52,height*.77,height*.58,height,height},6);
  }else for(int i=0;i<12;i++){
   double x=i*width/11,bh=height*(.14+(i*17%25)/100.0);g.setFill(Color.web(i%2==0?"#16232A":"#1C2B30"));g.fillRect(x,height*.75-bh,width/11-2,height);
   g.setFill(Color.web("#E8B84A",.45));for(int j=0;j<5;j++)g.fillRect(x+6,height*.75-bh+12+j*14,2,4);
  }
  g.setFill(new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,new Stop(0,Color.TRANSPARENT),new Stop(.5,Color.web("#0B0B0D",.15)),new Stop(1,Color.web("#0B0B0D"))));g.fillRect(0,0,width,height);
  Label kicker=Ui.label("NOIR / CINEMA  •  "+String.format("%02d",id),"poster-kicker"),name=Ui.label(title,"poster-title");name.setMaxWidth(width-32);name.setStyle("-fx-font-size: "+(width<200?20:24)+"px;");
  VBox copy=new VBox(10,kicker,name);copy.setPadding(new Insets(18));copy.setAlignment(Pos.BOTTOM_LEFT);
  StackPane poster=new StackPane(canvas,copy);poster.setMinSize(width,height);poster.setPrefSize(width,height);poster.setMaxSize(width,height);poster.setAccessibleText("Affiche minh hoạ: "+title);return poster;
 }
}
