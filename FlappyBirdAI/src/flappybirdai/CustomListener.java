package flappybirdai;

import static flappybirdai.FlappyBirdAI.speedUp;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

//Ekrana tıklayınca hızın değişmesini sağlayan kod bloğu
public class CustomListener implements MouseListener{

      public void mouseClicked(MouseEvent e) {
          speedUp = !speedUp;       //FlappyBirdAI sınıfında bulunan global değişken
      }

      public void mousePressed(MouseEvent e) {
      }

      public void mouseReleased(MouseEvent e) {
      }

      public void mouseEntered(MouseEvent e) {
      }

      public void mouseExited(MouseEvent e) {
      }
   }
