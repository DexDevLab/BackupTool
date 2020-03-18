package backuptool;


import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;


/**
 * @author Daniel Augusto Monteiro de Almeida
 * @since 26/02/2020
 * @version 0.1.0-20200317-280
 *
 * User Profile Backup Tool
 */
public class BackupTool extends Application
{

  @Override
  public void start(Stage stage) throws Exception
  {
    Parent root = FXMLLoader.load(getClass().getResource("FXMLMainWindow.fxml"));

    Scene scene = new Scene(root);
    stage.setScene(scene);
    stage.setResizable(false);
    stage.setTitle("Backup Tool");
    stage.getIcons().add(new Image(BackupTool.class.getResourceAsStream("icon1.png")));
    stage.setOnCloseRequest((e) ->
    {
      System.exit(0);
    });
    stage.show();
  }

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args)
  {
    launch(args);
  }

}
