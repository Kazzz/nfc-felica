/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kazzz;

import static net.kazzz.felica.lib.FeliCaLib.COMMAND_POLLING;
import static net.kazzz.felica.lib.FeliCaLib.COMMAND_READ_WO_ENCRYPTION;
import static net.kazzz.felica.lib.FeliCaLib.COMMAND_REQUEST_SERVICE;
import static net.kazzz.felica.lib.FeliCaLib.COMMAND_REQUEST_SYSTEMCODE;
import static net.kazzz.felica.lib.FeliCaLib.COMMAND_SEARCH_SERVICECODE;
import net.kazzz.felica.FeliCa;
import net.kazzz.felica.FeliCaException;
import net.kazzz.felica.command.PollingResponse;
import net.kazzz.felica.lib.FeliCaLib;
import net.kazzz.felica.lib.FeliCaLib.CommandPacket;
import net.kazzz.felica.lib.FeliCaLib.CommandResponse;
import net.kazzz.felica.lib.FeliCaLib.IDm;
import net.kazzz.felica.suica.Suica;
import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * FeliCa PICCのデータを読みこむリーダークラスのサンプル実装を提供します
 *
 * @author Kazzz
 * @date 2011/01/21
 * @since Android API Level 9
 *
 */

public class NFCFeliCaReader extends Activity implements OnClickListener {
    private String TAG = "NFCFelicaTagReader";

    private Parcelable nfcTag;
    //private IDm idm;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TextView tv_tag = (TextView) findViewById(R.id.result_tv);

        Button btnRead = (Button) findViewById(R.id.btn_read);
        btnRead.setOnClickListener(this);

        Button btnHistory = (Button) findViewById(R.id.brn_hitory);
        btnHistory.setOnClickListener(this);

        Button btnInout = (Button) findViewById(R.id.btn_inout);
        btnInout.setOnClickListener(this);
        btnInout.setEnabled(false);

        Intent intent = this.getIntent();
        String action = intent.getAction();

        // android.nfc.extra.TAG 退避
        this.nfcTag = intent.getParcelableExtra("android.nfc.extra.TAG");

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            try {
                String data = readData();
                tv_tag.setText(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        btnHistory.setEnabled(this.nfcTag != null);

        //IDm退避
        //idm = new IDm(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));


    }


    public void onClick(View v) {
        try {
            switch (v.getId()) {
                case R.id.btn_read:
                    this.runOnUiThread(new Thread(){
                        @Override
                        public void run() {
                            try {
                                TextView tv_tag = (TextView) findViewById(R.id.result_tv);
                                tv_tag.setText(readData());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    break;
                case R.id.brn_hitory:
                    this.runOnUiThread(new Thread(){
                        @Override
                        public void run() {
                            try {
                                TextView tv_tag = (TextView) findViewById(R.id.result_tv);
                                tv_tag.setText(readHistoryData());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * FeliCa 使用履歴を読み込みます
     *
     * @return
     */
    protected String readHistoryData() throws Exception {

        try {
            FeliCa f = new FeliCa(this.nfcTag);

            //polling は IDm、PMmを取得するのに必要
            f.polling(FeliCa.SYSTEMCODE_PASMO);

            //read
            byte addr = 0;
            byte[] result = f.readWithoutEncryption(FeliCa.SERVICE_SUICA_HISTORY, addr);

            StringBuilder sb = new StringBuilder();
            while ( result != null ) {
                sb.append("履歴 No.  " + (addr + 1) + "\n");
                sb.append("---------\n");
                sb.append("\n");
                Suica.History s = new Suica.History(result, this);
                sb.append(s.toString());
                sb.append("---------------------------------------\n");
                sb.append("\n");

                addr++;
                Log.d(TAG, "addr = " + addr);
                result = f.readWithoutEncryption(FeliCa.SERVICE_SUICA_HISTORY, addr);
            }

            String str = sb.toString();
            Log.d(TAG, str);
            return str;
        } catch (FeliCaException e) {
            e.printStackTrace();
            Log.e(TAG, "readHistoryData", e);
            throw e;
        }
    }


    /**
     * FeliCa データを読み込みます
     * @return String 読み込んだデータのダンプ結果が文字列で戻ります
     * @throws Exception
     */
    private String readData() throws Exception {
        assert this.nfcTag != null;

        StringBuilder sb = new StringBuilder();
        try {
            //polling data
            CommandPacket polling =
                new CommandPacket(COMMAND_POLLING
                    , new byte[]{
                          (byte)0x00, (byte)0x03 // FeliCaシステムコード
                        , (byte)0x01             //　システムコードリクエスト
                        , (byte)0x00});          // タイムスロット
            sb.append(polling.toString());
            CommandResponse r = FeliCaLib.execute(this.nfcTag, polling);
            PollingResponse pr = new PollingResponse(r);
            sb.append(pr.toString());
            sb.append("\n");
            sb.append("\n-----------------------------------------");
            sb.append("\n");

            IDm idm = r.getIDm();

            //request systemCode
            CommandPacket reqSystemCode =
                new CommandPacket(COMMAND_REQUEST_SYSTEMCODE, idm);
            sb.append(reqSystemCode.toString());
            r = FeliCaLib.execute(this.nfcTag, reqSystemCode);
            sb.append(r.toString());
            sb.append("\n");
            sb.append("\n-----------------------------------------");
            sb.append("\n");

            // search service code
            CommandPacket searchServiceCode =
                new CommandPacket(COMMAND_SEARCH_SERVICECODE, idm);

            sb.append(searchServiceCode.toString());
            r = FeliCaLib.execute(this.nfcTag, searchServiceCode);
            sb.append(r.toString());
            sb.append("\n");
            sb.append("\n-----------------------------------------");
            sb.append("\n");
            // search service code
            CommandPacket requestService =
                new CommandPacket(COMMAND_REQUEST_SERVICE, idm
                    , new byte[]{(byte) 0x01});
            sb.append(requestService.toString());
            r = FeliCaLib.execute(this.nfcTag, requestService);
            sb.append(r.toString());
            sb.append("\n");
            sb.append("\n-----------------------------------------");
            sb.append("\n");

            // search read without encryption (利用履歴)
            CommandPacket readWoEncrypt =
                new CommandPacket(COMMAND_READ_WO_ENCRYPTION, idm
                    , new byte[]{(byte) 0x01        // Number of Service
                        , (byte) 0x0F, (byte) 0x09  // サービスコード(利用履歴)
                        , (byte) 0x01               // 同時読み込みブロック数
                        , (byte) 0x80, (byte) 0x00, (byte) 0x00 });// ブロックリスト
            sb.append(readWoEncrypt.toString());
            r = FeliCaLib.execute(this.nfcTag, readWoEncrypt);
            sb.append(r.toString());
            sb.append("\n");
            sb.append("\n-----------------------------------------");
            sb.append("\n");

            // search read without encryption (入出場履歴)
            /*
            readWoEncrypt =
                new CommandPacket(COMMAND_READ_WO_ENCRYPTION, idm
                    , new byte[]{(byte) 0x01        // Number of Service
                        , (byte) 0x8F, (byte) 0x10  // サービスコード(入出場履歴)
                        , (byte) 0x01               // 同時読み込みブロック数
                        , (byte) 0x00 });           // ブロックリスト

            sb.append(readWoEncrypt.toString());
            r = FeliCa.execute(this.nfcTag, readWoEncrypt);
            sb.append(r.toString());
            sb.append("\n");
            sb.append("\n-----------------------------------------");
            sb.append("\n");

            // search read requestResponse
            CommandPacket requestResponse = new CommandPacket(COMMAND_REQUEST_RESPONSE, idm);
            sb.append(requestResponse.toString());
            r = FeliCa.execute(this.nfcTag, requestResponse);
            sb.append(r.toString());
            sb.append("\n");
            sb.append("\n------------------------------------------------");
            sb.append("\n");
            */
        } catch (Exception e) {
            e.printStackTrace();
        }

        String result = sb.toString();
        Log.d(TAG, result);
        return result;
   }
}
