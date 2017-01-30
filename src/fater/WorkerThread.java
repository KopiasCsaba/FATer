/**
 *
 * Copyright 2010 Kopiás Csaba [ http://kopiascsaba.hu ]
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package fater;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
 * @author Kopiás Csaba
 */
public class WorkerThread extends Thread {

    /**
     * Host class
     */
    private FaterMain fm;
    private int numberOfFiles;
    private int fileSize;
    /**
     * Target directory
     */
    private String target;
    private boolean running = true;
    private MessageDigest msgDigMd5 = null;
    /**
     * Modes we could be in
     */
    final static int MODE_WRITE = 0; // We are dumping out the files
    final static int MODE_READ = 1; // We are validating their checksums
    final static int MODE_WAIT = 2; // We are waiting on the user to unmount and remount the drive
    final static int MODE_FINISHED = 3; // We are finished
    private int mode = MODE_WRITE;

    public WorkerThread(FaterMain fm) {
        this.fm = fm;

        numberOfFiles = Integer.parseInt(fm.numberOfFiles.getText());
        fileSize = Integer.parseInt(fm.fileSize.getText()) * 1024;
        target = fm.targetDir.getText();

        try {
            msgDigMd5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("No such algorithm: MD5");
        }

    }

    public void run() {
        RandomString rs = new RandomString(fileSize);
        String content;
        String hash;
        File file;
        String[] hashes = new String[numberOfFiles];

        int i = 0;
        int skippedAt = -1;
        int errors = 0;
        boolean result;
        fm.progressBar.setMaximum(numberOfFiles * 2);

        fm.log("Creating " + numberOfFiles + " files with " + (fileSize / 1024) + " Kb random data...");

        while (running) {

            if (mode == MODE_WRITE) {
                file = getFile(target, i, true);
                if (i < numberOfFiles) {
                    content = rs.nextString();

                    if (fm.getFreeSpace() < fileSize) {
                        fm.log("No more space left @" + i + "! Skipping to next step...");
                        skippedAt = i;
                        i = numberOfFiles;
                        continue;
                    }
                    try {
                        writeToFile(file, content);
                    } catch (Exception e) {
                        fm.log("Creation of " + file.getAbsolutePath() + " failed: " + e.getLocalizedMessage() + " (Err#0)");
                    }
                    hashes[i] = new String(calcMd5(content));
                    i++;
                    fm.progressBar.setValue(i);
                } else {
                    i = 0;
                    mode = MODE_WAIT;
                    fm.log("Waiting for you to detach, unplug and replug the device...");
                    fm.setMode(FaterMain.MODE_WAIT);

                }
            } else if (mode == MODE_WAIT) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                }

            } else if (mode == MODE_READ) {

                if (i == 0) {
                    fm.log("Reading files and checking checksums");
                }
                file = getFile(target, i, false);
                if (i < numberOfFiles) {
                    if (skippedAt != -1 && i == skippedAt) {
                        fm.log("Finishing at " + i + "...");
                        mode = MODE_FINISHED;

                        continue;
                    }

                    result = file.isFile();
                    if (!result) {
                        fm.log(file.getName() + ": is not found! (Err#1)");
                    }

                    result &= file.canRead();
                    if (!file.canRead()) {
                        fm.log(file.getName() + ": is unreadable! (Err#2)");
                    }

                    hash = calcFileMd5(file);
                    result &= hash.equals(hashes[i]);
                    if (!hash.equals(hashes[i])) {
                        fm.log(file.getName() + ": is corrupted! (Err#3)");
                    }
//                    fm.log(hash+" = "+hashes[i]);

                    if (!result) {
                        errors++;
                    } else {
                        file.delete();
                    }
                    i++;
                    fm.progressBar.setValue(numberOfFiles + i);
                } else {
                    mode = MODE_FINISHED;
                    // remove the last dir if it is empty
                    new File(file.getParent()).delete();
                }

            } else { // Mode Finished

                fm.log("Finished. Number of errors " + errors + ".");
                fm.setMode(FaterMain.MODE_BEFORE);
                fm.progressBar.setValue(numberOfFiles * 2);
                shutdown();
            }
        }

    }

    public File getFile(String target, int i, boolean createdir) {
        int dp = 5000;
        int dira = Math.round(i / dp);
        int dirb = Math.round((i - 1) / dp);
        File f = new File(target + "/" + dira + "/" + i + ".txt");

        if (dira != dirb || i == 0) {
            fm.log("Switching to directory: " + dira);
            if (createdir) {
                new File(target + "/" + dira).mkdir();
            } else {
                new File(target + "/" + dirb).delete();
            }

        }

        return f;

    }

    public void shutdown() {
        running = false;

    }

    public void setMode(int mode) {
        target = fm.targetDir.getText();
        this.mode = mode;
    }

    public int getMode() {
        return mode;
    }

    public String calcMd5(String md5) {
        return calcMd5(md5.getBytes());
    }

    public String calcMd5(byte[] md5) {
        msgDigMd5.update(md5, 0, md5.length);

        String ret = new BigInteger(1, msgDigMd5.digest()).toString(16);
        return ret.length() == 31 ? "0" + ret : ret;
    }

    // Eredeti verzio: http://www.rgagnon.com/javadetails/java-0416.html
    public String calcFileMd5(File file) {

        try {
            InputStream fis = new FileInputStream(file);

            byte[] buffer = new byte[4096 * 2];

            int numRead;
            do {
                numRead = fis.read(buffer);

                if (numRead > 0) {
                    msgDigMd5.update(buffer, 0, numRead);
                }
            } while (numRead != -1);
            fis.close();

            String ret = new BigInteger(1, msgDigMd5.digest()).toString(16);

            return ret.length() == 31 ? "0" + ret : ret;

        } catch (Exception e) {
            return "";
        }
    }

    public void writeToFile(File file, String content) throws IOException {

        BufferedWriter bufferedWriter = null;

        try {
            //Construct the BufferedWriter object
            bufferedWriter = new BufferedWriter(new FileWriter(file));
            //Start writing to the output stream
            bufferedWriter.write(content);
        } catch (IOException ex) {
            bufferedWriter = null;
            throw (ex);
        } finally {
            //Close the BufferedWriter
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.flush();
                    bufferedWriter.close();
                    bufferedWriter = null;
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

    }
}
