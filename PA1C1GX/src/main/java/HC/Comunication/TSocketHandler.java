/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package HC.Comunication;

import HC.Entities.TCallCenter;
import HC.Monitors.*;
import HC.Entities.TPatient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * @author guids
 */
public class TSocketHandler extends Thread {

    private final Socket socket;

    public TSocketHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        TCallCenter callCenter = null;

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String inputLine;
            
            int NoA, NoC, NoS, PT, ET, MAT, TTM;
            String mode;

            while (true) {
                if ((inputLine = in.readLine()) != null) {
                    System.out.println(inputLine);
                    String[] clientMessage = inputLine.split(":");

                    switch (clientMessage[0]) {
                        //NoA:NoC:NoS:PT:ET:MAT:TTM:Mode
                        case "CONFIG":
                            NoA = Integer.parseInt(clientMessage[1]);
                            NoC = Integer.parseInt(clientMessage[2]);
                            NoS = Integer.parseInt(clientMessage[3]);
                            PT = Integer.parseInt(clientMessage[4]);
                            ET = Integer.parseInt(clientMessage[5]);
                            MAT = Integer.parseInt(clientMessage[6]);
                            TTM = Integer.parseInt(clientMessage[7]);
                            mode = clientMessage[8];

                            // Create Monitors
                            METH meth = new METH(NoS);
                            MEVH mevh = new MEVH();
                            MWTH mwth = new MWTH();
                            MMDH mmdh = new MMDH();
                            MPYH mpyh = new MPYH();

                            callCenter = new TCallCenter(meth, mevh, mwth, mmdh, mpyh);
                            
                            // Create Adult Patients
                            for(int i =0; i<NoA; i++){
                                TPatient p = new TPatient(true, meth);
                                p.start();
                            }
                            
                            // Create Child Patients
                            for(int i =0; i<NoA; i++){
                                TPatient p = new TPatient(false, meth);
                                p.start();
                            }
                            
                            break;
                        case "MODE":
                            if (clientMessage[1] == "AUT") {
                                callCenter.setAuto(true);
                            } else if (clientMessage[1] == "MAN") {
                                callCenter.setAuto(false);
                            }
                            break;
                        case "NEXT":
                            callCenter.allowNextPatient();
                            break;
                        case "END":
                            socket.close();
                            System.exit(0);
                            break;
                    }
                }

            }
        } catch (IOException e) {
            System.err.println("Socket error");
        }
    }
}
