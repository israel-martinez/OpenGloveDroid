package com.israel.martinez.openglovedroid.IO;

import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Created by israel-martinez on 18-06-18.
 */

public class CSV {
    public String mFolderName;
    public String mFileName;
    public String mExternalStoragePath;

    public CSV(String mFolderName, String mFileName) {
        this.mFolderName = mFolderName;
        this.mFileName = mFileName;
    }

    // Write input data array to one column Data File
    public void write(ArrayList<Long> values, String columnTitle) {
        try {

            File folder = new File(Environment.getExternalStorageDirectory()
                    + "/" + mFolderName);

            boolean success = true;
            if (!folder.exists())
                success = folder.mkdirs();

            if(success){
                final String pathName = folder.toString() + "/" + mFileName;
                mExternalStoragePath = pathName;

                PrintWriter pw = new PrintWriter(new File(pathName));
                pw.write(columnTitle + '\n');

                for (Long value:
                     values) {
                    pw.write(value.toString() + '\n');
                }
                pw.close();
                System.out.println("done!");
            }else{
                System.out.println("Folder no created");
            }

        }catch(IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "FolderName: " + mFolderName +
                        "\nFileName: " + mFileName +
                        "\nExternal StoragePath:" + mExternalStoragePath;
    }
}
