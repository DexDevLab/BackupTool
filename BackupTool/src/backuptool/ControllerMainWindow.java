package backuptool;


import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;


/**
 * Main Window Controller.
 */
public class ControllerMainWindow implements Initializable
{
  /** String for appending textbox / scroll. */
  StringProperty textLog = new SimpleStringProperty();
  /** DateFormat for logs and main folder creation. */
  DateFormat df = new SimpleDateFormat("yyyy-MM-dd--HH.mm.ss");
  /** Logger instance. */
  Logger Log = new Logger();
  /** Log directory. */
  File logdir = new File("C:/BackupTool/logs");
  /** backup selected destination folder. */
  File destDir;
  /** Retrieves complete main backup folder name. */
  File bkpRoot;
  /** String version of the main backup folder name. */
  String bkpRootStr;
  /** Retrieves hostname. */
  String hostname;
  /** Retrieves timestamp for main backup folder. */
  String currentTimestamp;
  /** List containing located username folders. */
  List<String> foundUsers = new ArrayList<>();
  /** List containing username folders to backup after file filtering. */
  List<String> foldersToBackup = new ArrayList<>();
  /** Variable for calculating the elapsed time. */
  long t1, t2;
  /** Variable for calculating file / folder sizes. */
  double globalSize, fileSize = 0;

  /** Text Area containing detailed progress. */
  @FXML
  private TextArea textAreaWindowLog;

  /** Text Area storing program version. */
  @FXML
  private Label labelVersion;

  /** Button for pick backup destination. */
  @FXML
  private Button buttonProcurar;

  /** Label informing progress. */
  @FXML
  private Label labelAndamento;

  /** Button for starting procedure. */
  @FXML
  private Button buttonBkpStart;

  /** Text Field containing backup destination path. */
  @FXML
  private TextField textFieldPath;

  /** Progress Bar indicating program activity. */
  @FXML
  private ProgressBar progressBar;

  /**
   * Event for clicking on button to pick the backup destination folder.
   * @see #buttonProcurar
   */
  @FXML
  void actionButtonProcurar()
  {
    Stage stage = new Stage();
    DirectoryChooser dc = new DirectoryChooser();
    destDir = dc.showDialog(stage);
    if (destDir != null) { textFieldPath.setText(destDir.getPath()); }
  }

  /**
   * Event for clicking on button to pick the backup destination folder.
   * @see #buttonBkpStart
   */
  @FXML
  void actionButtonStart()
  {
    File check = new File (textFieldPath.getText());
    if (!check.exists()) { Platform.runLater(new FutureTask<>(new AskForValidFolder())); }
    else
    {
      labelAndamento.setVisible(true);
      progressBar.setVisible(true);
      buttonProcurar.setDisable(true);
      buttonBkpStart.setDisable(true);
      new Thread(new MainTask()).start();
      t1 = System.currentTimeMillis();
    }
  }

  /**
   * Initialization Method.
   * @param url
   * @param rb
   */
  @Override
  public void initialize(URL url, ResourceBundle rb)
  {
    Log.setMessageFormat("yyyy/MM/dd HH:mm:ss");
    Log.setLogNameFormat("yyyy-MM-dd--HH.mm.ss");
    Log.setLogDir(logdir);
    Log.log(null, "INFO", "Logger inicializado.");
    textAreaWindowLog.textProperty().bind(textLog);
    labelVersion.setText("v0.1.0-20200317-280");
    textLog.setValue("");
    buttonProcurar.setTooltip(new Tooltip("Escolha um diretório para salvar o backup"));
    buttonBkpStart.setTooltip(new Tooltip("Clique para iniciar o processo"));
    Tooltip tooltipPath = new Tooltip("Selecione um local de destino");
    textFieldPath.setTooltip(tooltipPath);

    textFieldPath.textProperty().addListener((observable, oldValue, newValue) ->
    {
      if (textFieldPath.getText().matches("[a-zA-z|[0-9]|[\\W]]+")) { tooltipPath.setText(textFieldPath.getText()); }
      else { tooltipPath.setText("Selecione um local de destino"); }
    });

    textLog.addListener((observable, oldValue, newValue) ->
    {
      textAreaWindowLog.selectPositionCaret(textAreaWindowLog.getLength());
      textAreaWindowLog.deselect();
    });

  }

  /** Main process Task Class. */
  class MainTask extends Task<Integer>
  {

    /** Perform process method, finish status info, counting total size and elapsed time. */
    @Override
    protected Integer call()
    {
      runTask();
      Platform.runLater(() -> { labelAndamento.setText("CONCLUÍDO"); });
      Platform.runLater(() -> { labelAndamento.setTextFill(Color.RED); });
      Platform.runLater(() -> { labelAndamento.setLayoutX(136); });
      Platform.runLater(() -> { progressBar.setVisible(false); });
      Platform.runLater(() -> { buttonProcurar.setDisable(false); });
      Platform.runLater(() -> { buttonBkpStart.setDisable(false); });
      t2 = System.currentTimeMillis();
      long deltaTSeconds = (t2-t1)/1000;
      long minutes = deltaTSeconds / 60;
      long seconds = deltaTSeconds % 60;
      screenLog(500, "---------------------");
      screenLog(500, "---------------------");
      String bkpDirSize = FileUtils.byteCountToDisplaySize(FileUtils.sizeOfDirectory(bkpRoot));
      String userDirSize = (FileUtils.byteCountToDisplaySize((long) globalSize));
      screenLog(500, "Copiados " + bkpDirSize + " de " + userDirSize + " totais.");
      screenLog(500, "CONCLUÍDO EM " + minutes + " MINUTO(S) E " + seconds + " SEGUNDO(S).");
      return 0;
    }

    /** Main procedure. */
    private void runTask()
    {
      screenLog(500, "Iniciando processo...");
      screenLog(500, "---------------------");
      screenLog(500, "Verificando...");
      screenLog(500, "---------------------");
      // Getting Hostname
      ProcessBuilder host = new ProcessBuilder("cmd", "/c", "hostname");
      try
      {
        hostname = IOUtils.toString(host.start().getInputStream(), StandardCharsets.UTF_8);
        hostname = hostname.trim();
        Log.log(null, "INFO", "Hostname da máquina: " + hostname);
      }
      catch (IOException ex) { Log.log(ex, "ERRO", "FALHA AO EXECUTAR O COMANDO DE DEFINIR O HOSTNAME"); }
      currentTimestamp = df.format(Calendar.getInstance().getTime());
      bkpRootStr = (destDir + hostname + "--" + currentTimestamp);
      // Setting backup destination folder as path + hostname + timestamp
      screenLog(500, "Backup será salvo em " + bkpRootStr);
      screenLog(500, "---------------------");
      Platform.runLater(() -> { textFieldPath.setText(bkpRootStr); } );
      bkpRoot = new File(bkpRootStr);
      if (!bkpRoot.exists()) { bkpRoot.mkdirs(); }
      // First filtering. This recursive ONLY VALIDATES:
      // Usernames with numbers on it (i. e., 535260, etc)
      // Usernames with the letter "t", outsource employees (t04693, t00474, etc)
      // Usernames created temporarily bc of some problem with winlogon / user profiling (*.TEMP, etc)
      try
      {
        Files.list(new File("C:/Users").toPath()).limit(999)
          .forEach(path ->
          {
            String pathtest = path.toString().replace("C:\\Users\\", "");
            if (pathtest.matches("[0-9]+") | pathtest.startsWith("t") | pathtest.contains("TEMP") | pathtest.contains("LACTALISBRA"))
            {
              screenLog(500, "Encontrado: " + path);
              foundUsers.add(path.toString());
            }
            else
            {
              File filetest = new File (path.toString());
              if (filetest.isDirectory()) { Log.log(null, "INFO", "Diretório encontrado em " + path + " NÃO será backupeado."); }
              else { Log.log(null, "INFO", "Arquivo " + path + " NÃO será backupeado."); }
            }
          });
      }
      catch (IOException ex) { Log.log(ex, "ERRO", "EXCEÇÃO DURANTE A VARREDURA NAS PASTAS RAIZ."); }
      screenLog(500, "---------------------");
      screenLog(500, "Verificando presença de arquivos nos diretórios de usuário...");
      Thread a = new Thread(() ->
      {
        foundUsers.forEach((dirpath) ->
        {
          // Here it scans for files or directories which are obvious for backup, in order.
          // First, look for the Google Chrome Bookmarks.
          // Second, for files on Google Apps Sync (may have PST for mail)
          // Third, for PSTs on Microsoft Outlook Cache (saved there by accident)
          // Then, the whole user folder
          screenLog(500, "Verificando " + dirpath + "...");
          File dircheck = new File(dirpath);
          File chromeBookmarks = new File(dircheck + "\\AppData\\Local\\Google\\Chrome\\User Data\\Default\\bookmarks");
          File gAppsSyncPST = new File(dircheck + "\\AppData\\Local\\Google\\Google Apps Sync");
          File pstMSO1 = new File(dircheck + "\\AppData\\Local\\Microsoft\\Outlook");
          File pstMSO2 = new File(dircheck + "\\AppData\\Roaming\\Microsoft\\Outlook");
          boolean pst1 = false;
          boolean pst2 = false;
          if (pstMSO1.exists()) { pst1 = scan(pstMSO1, false); }
          if (pstMSO2.exists()) { pst2 = scan(pstMSO2, false); }
          if (chromeBookmarks.exists() || gAppsSyncPST.exists() || scan(dircheck, true) || pst1 || pst2)
          {
            double totalSize = 0;
            File refDir;
            if (chromeBookmarks.exists())
            {
              totalSize = FileUtils.sizeOf(chromeBookmarks);
              screenLog(500, "Favoritos do Google Chrome: " + (FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(chromeBookmarks))));
            }
            if (gAppsSyncPST.exists())
            {
              totalSize += FileUtils.sizeOfDirectory(gAppsSyncPST);
              screenLog(500, "PSTs do Google Apps: " + (FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(gAppsSyncPST))));
            }
            refDir = (new File(dirpath + "\\Desktop"));
            retrieveSize(refDir);
            screenLog(500, "Pasta Desktop: " + (FileUtils.byteCountToDisplaySize((long) fileSize)));
            fileSize = 0;
            totalSize += checkSizeOfDirectory(refDir);
            refDir = (new File(dirpath + "\\Documents"));
            retrieveSize(refDir);
            screenLog(500, "Pasta Documentos: " + (FileUtils.byteCountToDisplaySize((long) fileSize)));
            fileSize = 0;
            totalSize += checkSizeOfDirectory(refDir);
            refDir = (new File (dirpath + "\\Downloads"));
            retrieveSize(refDir);
            screenLog(500, "Pasta Downloads: "  + (FileUtils.byteCountToDisplaySize((long) fileSize)));
            fileSize = 0;
            totalSize += checkSizeOfDirectory(refDir);
            refDir = (new File (dirpath + "\\Favorites"));
            retrieveSize(refDir);
            screenLog(500, "Pasta Favoritos: " + (FileUtils.byteCountToDisplaySize((long) fileSize)));
            fileSize = 0;
            totalSize += checkSizeOfDirectory(refDir);
            refDir = (new File (dirpath + "\\Pictures"));
            retrieveSize(refDir);
            screenLog(500, "Pasta Imagens: " + (FileUtils.byteCountToDisplaySize((long) fileSize)));
            fileSize = 0;
            totalSize += checkSizeOfDirectory(refDir);
            refDir = (new File (dirpath + "\\Music"));
            retrieveSize(refDir);
            screenLog(500, "Pasta Músicas: " + (FileUtils.byteCountToDisplaySize((long) fileSize)));
            fileSize = 0;
            totalSize += checkSizeOfDirectory(refDir);
            refDir = (new File (dirpath + "\\Videos"));
            retrieveSize(refDir);
            screenLog(500, "Pasta Vídeos: " + (FileUtils.byteCountToDisplaySize((long) fileSize)));
            fileSize = 0;
            totalSize += checkSizeOfDirectory(refDir);
            // This recursive checks for uncommon user directories placed on user profile root folder.
            File[] filcheck = dircheck.listFiles(new FileFilter()
            {
              @Override
              public boolean accept(File file)
              {
                BigInteger size1 = FileUtils.sizeOfAsBigInteger(file);
                BigInteger cap1 = BigInteger.valueOf(5120);
                int comparator1 = size1.compareTo(cap1);
                if (file.isDirectory()) { return ((comparator1 == 1) && lookup (file, true, true)); }
                return false;
              }
            });
            for (int i = 0; i <= filcheck.length-1; i++)
            {
              if ((!filcheck[i].getName().contains(".")))
              {
                retrieveSize(filcheck[i]);
                totalSize += FileUtils.sizeOfDirectory(filcheck[i]);
                screenLog(500, "Pasta " + filcheck[i] + ": " + (FileUtils.byteCountToDisplaySize((long) fileSize)));
                fileSize = 0;
              }
            }
            // The same as recursive above, but common files.
            File[] filcount = dircheck.listFiles(new FileFilter()
            {
              @Override
              public boolean accept(File file)
              {
                BigInteger size1 = FileUtils.sizeOfAsBigInteger(file);
                BigInteger cap1 = BigInteger.valueOf(5120);
                int comparator1 = size1.compareTo(cap1);
                return ((comparator1 == 1) && lookup (file, false, false));
              }
            });
            for (int j = 0; j <= filcount.length-1; j++)
            {
              fileSize = FileUtils.sizeOf(filcount[j]);
              totalSize += fileSize;
              screenLog(500, "Arquivo " + filcount[j] + ": " + (FileUtils.byteCountToDisplaySize((long) fileSize)));
              fileSize = 0;
            }
            screenLog(500, "---------------------");
            screenLog(500, "---------------------");
            screenLog(500, "Diretório " + dirpath + " contém dados de usuário e será backupeado.");
            screenLog(500, "Tamanho total da pasta do usuário: " + (FileUtils.byteCountToDisplaySize((long) totalSize)));
            globalSize += totalSize;
            screenLog(500, "---------------------");
            screenLog(500, "---------------------");
            foldersToBackup.add(dirpath);
          }
          else
          {
            screenLog(500, "---------------------");
            screenLog(500, "---------------------");
            screenLog(500, "Diretório " + dirpath + " NÃO contém dados de usuário.");
            screenLog(500, "---------------------");
            screenLog(500, "---------------------");
          }
        });
      });
      a.start();
      try { a.join(); }
      catch (InterruptedException ex) { Log.log(ex, "ERRO", "THREAD INTERROMPIDA."); }
      screenLog(500, "---------------------");
      screenLog(500, "---------------------");
      screenLog(500, "Tamanho total do backup a ser feito: " + (FileUtils.byteCountToDisplaySize((long) globalSize)));
      screenLog(500, "Iniciando processo de backup...");
      screenLog(500, "---------------------");
      screenLog(500, "---------------------");
      // Copying confirmed user directories
      foldersToBackup.forEach((dirpath) ->
      {
        File target = new File (dirpath);
        File userDir = new File(bkpRootStr + "\\" + dirpath.replace("C:\\Users\\", ""));
        if (!userDir.exists()) { userDir.mkdirs(); }
        File chromeBookmarks = new File(dirpath + "\\AppData\\Local\\Google\\Chrome\\User Data\\Default\\bookmarks");
        File gAppsSyncPST = new File(dirpath + "\\AppData\\Local\\Google\\Google Apps Sync");
        File pstMSO1 = new File(dirpath + "\\AppData\\Local\\Microsoft\\Outlook");
        File pstMSO2 = new File(dirpath + "\\AppData\\Roaming\\Microsoft\\Outlook");
        if (chromeBookmarks.exists())
        {
          File chromeBookmarksDest = new File(userDir + "\\AppData\\Local\\Google\\Chrome\\User Data\\Default\\bookmarks");
          if (!chromeBookmarksDest.exists()) { chromeBookmarksDest.mkdirs(); }
          try
          {
            Files.copy(chromeBookmarks.toPath(), chromeBookmarksDest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (chromeBookmarksDest.exists()) { screenLog(10, "Google Chrome Bookmarks copiados com sucesso."); }
          }
          catch (IOException ex) { Log.log(ex, "ERRO", "FALHA AO COPIAR OS BOOKMARKS DO GOOGLE CHROME."); }
        }
        if (gAppsSyncPST.exists())
        {
          File gAppsSyncPSTDest = new File(userDir + "\\AppData\\Local\\Google");
          copyAction(gAppsSyncPST, gAppsSyncPSTDest);
        }
        if (pstMSO1.exists())
        {
          File pstMSO1Dest = new File(userDir + "\\AppData\\Local\\Microsoft\\Outlook");
          copyFound(pstMSO1, pstMSO1Dest, false, false);
        }
        if (pstMSO2.exists())
        {
          File pstMSO2Dest = new File(userDir + "\\AppData\\Roaming\\Microsoft\\Outlook");
          copyFound(pstMSO2, pstMSO2Dest, false, false);
        }
        copyFound(target, userDir, true, true);
        copyFound(target, userDir, false, false);
        copyFound(target, userDir, true, false);
        screenLog(500, "---------------------");
      });
    }

    /**
     * Retrieves the size of directory, if it exists.
     * @return the folder size in bytes.
     */
    private long checkSizeOfDirectory(File dir)
    {
      if (dir.exists()) { return FileUtils.sizeOfDirectory(dir); }
      else { return 0; }
    }

    /** Updates fileSize variable for calculation. */
    private void retrieveSize(File dir)
    {
      if (dir.exists()) { fileSize = FileUtils.sizeOfDirectory(dir); }
    }

    /** Method for looking up for folder names by specific criteria,
     * or just scan for files on user folder.
     * @param file - the directory to search
     * @param isDir - if it is to search for directories or files
     * @param lookForMiscDirs - if it is to look for common directories
     * (Documents, Pictures, etc) of other directories on user root folder
     * @return true of false for matching the criteria.
     */
    private boolean lookup(File file, boolean isDir, boolean lookForMiscDirs)
    {
      if (isDir)
      {
        if (lookForMiscDirs)
        {
          return (
                !file.toString().contains("AppData")
                && !file.toString().contains("Desktop")
                && !file.toString().contains("Documents")
                && !file.toString().contains("Downloads")
                && !file.toString().contains("Favorites")
                && !file.toString().contains("Pictures")
                && !file.toString().contains("Music")
                && !file.toString().contains("Videos")
               );
        }
        else
        {
          return (
                ((!file.toString().contains("AppData"))
                && (file.toString().contains("Desktop"))
                || (file.toString().contains("Documents"))
                || (file.toString().contains("Downloads"))
                || (file.toString().contains("Favorites"))
                || (file.toString().contains("Pictures"))
                || (file.toString().contains("Music"))
                || (file.toString().contains("Videos")))
               );
        }
      }
      else
      {
        return (
                file.getName().toLowerCase().endsWith(".pst")
              || file.getName().toLowerCase().endsWith(".txt")
              || file.getName().toLowerCase().endsWith(".xls")
              || file.getName().toLowerCase().endsWith(".pdf")
              || file.getName().toLowerCase().endsWith(".msg")
              || file.getName().toLowerCase().endsWith(".xlsb")
              || file.getName().toLowerCase().endsWith(".xlsx")
              || file.getName().toLowerCase().endsWith(".docx")
              || file.getName().toLowerCase().endsWith(".doc")
               );
      }
    }

    /** Check if the source and the destinarion are valid file / folder
     *  and perform copying with logging.
     * @param src - The source file / directory to be copied
     * @param dest - The destination file or directory
     *
     */
    private void copyAction(File src, File dest)
    {
      try
      {
        Thread b = new Thread(() ->
        {
          try
          {
            if (src.isDirectory())
            {
              if (!src.getName().contains("."))
              {
                screenLog(10, "Copiando " + src + "...");
                FileUtils.copyDirectoryToDirectory(src, dest);
              }
            }
            else
            {
              if (dest.isDirectory())
              {
                screenLog(10, "Copiando " + src + "...");
                FileUtils.copyFileToDirectory(src, dest, false);
              }
              else
              {
                screenLog(10, "Copiando " + src + "...");
                FileUtils.copyFile(src, dest, false);
              }
            }
          }
          catch (IOException ex){}
        });
        b.start();
        b.join();
      }
      catch (InterruptedException ex){}
    }

    /** Validate the correct directories in order to copy.
     *
     * @param file - The source file to copy
     * @param dest - The destination file from the copy
     * @param dirLookup - if it is to look for directories or files
     * @param lookForMiscDirs  - if it is to look for non-common
     * folders (Desktop, Documents, etc)
     */
    private void copyFound(File file, File dest, boolean dirLookup, boolean lookForMiscDirs)
    {
      if (!file.isHidden())
      {
        File[] filcheck = (file).listFiles(new FileFilter()
        {
          @Override
          public boolean accept(File file)
          {
            if (lookForMiscDirs) { return (lookup(file, dirLookup, true) && file.isDirectory()); }
            else { return lookup(file, dirLookup, false); }
          }
        });
        for (int i = 0; i <= filcheck.length-1; i++) { copyAction(filcheck[i], dest); }
      }
    }

    /** Scan for specific file inside directories and subdirectories.
     *
     * @param file - The file/folder to verify
     * @param checkSubdirs - true to verify the subdirectories for files
     * @return true for specific user files be found
     */
    private boolean scan(File file, boolean checkSubdirs)
    {
      boolean verifier = false;
      try
      {
        if (file.getCanonicalFile().equals(file.getAbsoluteFile()))
        {
          File[] filcheck = file.listFiles(new FileFilter()
          {
            @Override
            public boolean accept(File file)
            {
              BigInteger size1 = FileUtils.sizeOfAsBigInteger(file);
              BigInteger cap1 = BigInteger.valueOf(5120);
              int comparator1 = size1.compareTo(cap1);
              boolean acc = false;
              if ((comparator1 == 1) && lookup (file, true, false)) { acc = file.isDirectory() || lookup (file, true, false); }
              return acc;
            }
          });
          for (int i = 0; i <= filcheck.length-1; i++)
          {
            if (filcheck[i].isDirectory() && checkSubdirs)
            {
              screenLog(10, "Verificando " + filcheck[i] + "...");
              BigInteger size = FileUtils.sizeOfAsBigInteger(filcheck[i]);
              BigInteger cap = BigInteger.valueOf(5120);
              int comparator = size.compareTo(cap);
              if (comparator == 1) { verifier = scan(filcheck[i], true); }
            }
            else { screenLog(5, "Arquivo de usuário " + filcheck[i] + " encontrado."); }
            verifier = true;
          }
        }
      }
      catch (IOException ex) { Log.log(ex, "ERRO", "EXCEÇÃO EM scan(File)"); }
      return verifier;
    }

    /** Method to update the text on screen.
     *
     * @param milis - miliseconds to wait for next log line
     * @param text - the text for output
     */
    private void screenLog(long milis, String text)
    {
      new Thread(() ->
      {
        Log.log(null, "INFO", text);
        Platform.runLater(() -> { textLog.setValue(textLog.getValue() + text + "\n"); });
      }).start();
      try { Thread.sleep(milis); }
      catch (InterruptedException ex) { Log.log(ex, "ERRO", "THREAD INTERROMPIDA EM screenLog(String)"); }
    }
  }

  /** Alert when user determinates a invalid folder for backup destination. */
  class AskForValidFolder implements Callable
  {

    @Override
    public AskForValidFolder call()
    {
      Alert alrt = new Alert(Alert.AlertType.ERROR," ", ButtonType.OK);
      alrt.initModality(Modality.APPLICATION_MODAL);
      Stage stage = (Stage) alrt.getDialogPane().getScene().getWindow();
      stage.getIcons().add(new Image(ControllerMainWindow.class.getResourceAsStream("icon1.png")));
      alrt.setTitle("Destino Inválido");
      alrt.setHeaderText("");
      alrt.setContentText("Selecione um diretório de destino válido para o backup.");
      ((Stage) alrt.getDialogPane().getScene().getWindow()).setAlwaysOnTop(true);
      Optional<ButtonType> result = alrt.showAndWait();
      if (result.get() == ButtonType.OK){}
      {
        Platform.runLater(() -> { textFieldPath.setText(""); });
        alrt.close();
      }
      return null;
    }

  }

}