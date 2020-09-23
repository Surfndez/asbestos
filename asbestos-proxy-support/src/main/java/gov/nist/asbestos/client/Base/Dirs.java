package gov.nist.asbestos.client.Base;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Dirs {

    static public void deleteDir(File dir) throws IOException {
        FileUtils.deleteDirectory(dir);
    }

    static public List<File> listOfFiles(File root) {
        List<File> list = new ArrayList<>();

        File[] aList = root.listFiles();
        if (aList != null) {
            for (File file : aList) {
                if (file.getName().startsWith(".")) continue;
                if (file.getName().startsWith("_")) continue;
                list.add(file);
            }
        }

        return list;
    }

    static public List<File> listOfDirectories(File root) {
        List<File> list = new ArrayList<>();

        File[] aList = root.listFiles();
        if (aList != null) {
            for (File file : aList) {
                if (!file.isDirectory()) continue;
                if (file.getName().startsWith(".")) continue;
                if (file.getName().startsWith("_")) continue;
                list.add(file);
            }
        }

        return list;
    }

    public static List<String> dirListingAsStringList(File dir) {
        List<String> contents = new ArrayList<>();

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isDirectory()) continue;
                if (file.getName().startsWith(".")) continue;
                if (file.getName().startsWith("_")) continue;
                contents.add(file.getName());
            }
            contents = contents.stream().sorted().collect(Collectors.toList());
        }

        return contents;
    }

}
