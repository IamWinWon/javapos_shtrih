/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.shtrih.jpos.fiscalprinter;

import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import com.shtrih.util.Hex;
import com.shtrih.util.CompositeLogger;
import com.shtrih.fiscalprinter.SMFiscalPrinter;
import com.shtrih.fiscalprinter.command.FDOParameters;


/**
 * @author V.Kravtsov
 */
public class FSService implements Runnable {

    private CompositeLogger logger = CompositeLogger.getLogger(FSService.class);

    
    FDOParameters parameters;
    private int pollPeriod;
    private int connectTimeout;
    private Thread thread = null;
    private boolean stopFlag = false;
    private final SMFiscalPrinter printer;

    public FSService(SMFiscalPrinter printer, FptrParameters parameters) {
        this.printer = printer;
        this.connectTimeout = parameters.FSConnectTimeout;
    }

    public void run() {
        try {
            while (true) {
                try 
                {
                    parameters = printer.readFDOParameters();
                    break;
                } catch (Exception e) {
                    Thread.sleep(10 * 1000);
                    logger.error("Failed to read FSSender parameters", e);
                }
            }

            while (!stopFlag) {
                checkData();
                Thread.sleep(pollPeriod);
            }
        } catch (InterruptedException e) {
            logger.error("InterruptedException", e);
            Thread.currentThread().interrupt();
        }
    }

    private void checkData() {
        try {
            byte[] data = printer.fsReadBlockData();

            if (data.length == 0) {
                return;
            }

            // System.out.println("FS -> OFD: " + Hex.toHex(data));

            byte[] answer = sendData(data);
            if (answer.length == 0) {
                return;
            }

            // System.out.println("FS <- OFD: " + Hex.toHex(answer));

            printer.fsWriteBlockData(answer);

        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private byte[] sendData(byte[] data) throws Exception {
        Socket socket = new Socket();
        socket.setSoTimeout(connectTimeout);
        socket.connect(new InetSocketAddress(parameters.getHost(), 
                Integer.valueOf(parameters.getPort())));
        socket.getOutputStream().write(data);
        InputStream in = socket.getInputStream();

        int headerSize = 30;
        byte[] header = new byte[headerSize];

        Read(in, header, headerSize);

        int size = ((header[25] << 8)) | (header[24] & 0xFF);

        byte[] answer = new byte[headerSize + size];
        System.arraycopy(header, 0, answer, 0, headerSize);
        if (size > 0) {
            Read(in, answer, headerSize, size);
        }
        return answer;
    }

    private void Read(InputStream in, byte[] buffer, int count) throws IOException {
        Read(in, buffer, 0, count);
    }

    private void Read(InputStream in, byte[] buffer, int offset, int count) throws IOException {
        int readCount = 0;
        while (readCount < count) {
            readCount += in.read(buffer, offset + readCount, count - readCount);
        }
    }

    private boolean isStarted() {
        return thread != null;
    }

    public void start() throws Exception {
        if (!isStarted()) {
            stopFlag = false;
            thread = new Thread(this);
            thread.start();
        }
    }

    public void stop() throws Exception {
        stopFlag = true;
        if (thread != null)
            thread.join();

        thread = null;
    }
}
