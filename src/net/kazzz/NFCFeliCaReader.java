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

import net.kazzz.felica.FeliCaException;
import net.kazzz.felica.FeliCaLiteTag;
import net.kazzz.felica.FeliCaTag;
import net.kazzz.felica.command.ReadResponse;
import net.kazzz.felica.lib.FeliCaLib;
import net.kazzz.felica.lib.FeliCaLib.IDm;
import net.kazzz.felica.lib.FeliCaLib.MemoryConfigurationBlock;
import net.kazzz.felica.lib.FeliCaLib.ServiceCode;
import net.kazzz.felica.lib.FeliCaLib.SystemCode;
import net.kazzz.felica.suica.Suica;
import android.app.Activity;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
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
    private boolean iSFeliCaLite;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        //IMEを自動起動しない
        this.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        
        setContentView(R.layout.main);
        TextView tv_tag = (TextView) findViewById(R.id.result_tv);

        Button btnRead = (Button) findViewById(R.id.btn_read);
        btnRead.setOnClickListener(this);

        Button btnHistory = (Button) findViewById(R.id.btn_hitory);
        btnHistory.setOnClickListener(this);
        btnHistory.setEnabled(false);

        Button btnWrite = (Button) findViewById(R.id.btn_write);
        btnWrite.setOnClickListener(this);
        btnWrite.setEnabled(false);

        Button btnInout = (Button) findViewById(R.id.btn_inout);
        btnInout.setOnClickListener(this);
        btnInout.setEnabled(false);

        Intent intent = this.getIntent();
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)) {
            // android.nfc.extra.TAG 退避
            this.nfcTag = intent.getParcelableExtra("android.nfc.extra.TAG");

            try {
                FeliCaLib.IDm idm = 
                    new FeliCaLib.IDm(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID));

                if ( idm == null ) {
                    throw new FeliCaException("Felica IDm を取得できませんでした");
                }
                
                this.iSFeliCaLite = this.iSFeliCaLite();
                  
                btnHistory.setEnabled(!this.iSFeliCaLite);
                btnWrite.setEnabled(this.iSFeliCaLite);
                
                String data = readData();
                tv_tag.setText(data);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }
        btnHistory.setEnabled(!this.iSFeliCaLite);
        btnWrite.setEnabled(this.iSFeliCaLite);
    }

    /**
     * FeliCa Liteデバイスか否かを検査します
     * @return boolean 読み込み対象がFeliCa Liteの場合trueが戻ります
     * @throws FeliCaException
     */
    private boolean iSFeliCaLite() throws FeliCaException {
        FeliCaTag f = new FeliCaTag(this.nfcTag);
        //polling は IDm、PMmを取得するのに必要
        IDm idm = f.pollingAndGetIDm(FeliCaLib.SYSTEMCODE_FELICA_LITE);
        return idm != null;
    }
    
    public void onClick(final View v) {
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
                case R.id.btn_write:
                    this.runOnUiThread(new Thread(){
                        @Override
                        public void run() {
                            try {
                                Intent intent = new Intent(NFCFeliCaReader.this, FeliCaLiteWriter.class);
                                intent.putExtra("nfcTag", NFCFeliCaReader.this.nfcTag);
                                startActivity(intent);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });

                    break;
                case R.id.btn_hitory:
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
            FeliCaTag f = new FeliCaTag(this.nfcTag);

            //polling は IDm、PMmを取得するのに必要
            f.polling(FeliCaLib.SYSTEMCODE_PASMO);

            //read
            ServiceCode sc = new ServiceCode(FeliCaLib.SERVICE_SUICA_HISTORY);
            byte addr = 0;
            ReadResponse result = f.readWithoutEncryption(sc, addr);

            StringBuilder sb = new StringBuilder();
            while ( result != null && result.getStatusFlag1() == 0  ) {
                sb.append("履歴 No.  " + (addr + 1) + "\n");
                sb.append("---------\n");
                sb.append("\n");
                Suica.History s = new Suica.History(result.getBlockData(), this);
                sb.append(s.toString());
                sb.append("---------------------------------------\n");
                sb.append("\n");

                addr++;
                //Log.d(TAG, "addr = " + addr);
                result = f.readWithoutEncryption(sc, addr);
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
        StringBuilder sb = new StringBuilder();
        try {
            
            if ( this.iSFeliCaLite ) {
                sb.append("\n");
                sb.append("FeliCa Lite デバイスです ");
                sb.append("\n----------------------------------------");
                sb.append("\n");
                // FeliCa Lite 読み込み
                FeliCaLiteTag ft = new FeliCaLiteTag(this.nfcTag);
                ft.polling();
                sb.append("  " + ft.toString());
                sb.append("\n----------------------------------------");
                sb.append("\n");
                
                //0ブロック目読み込み
                ReadResponse rr = ft.readWithoutEncryption((byte)0);
                sb.append("  " + rr.toString());
                sb.append("\n----------------------------------------");
                sb.append("\n");
                
                //MemoryConfig 読み込み
                MemoryConfigurationBlock mb = ft.getMemoryConfigBlock(); 
                sb.append("  " + mb.toString());
                sb.append("\n----------------------------------------");
                sb.append("\n");
                
                String result = sb.toString();
                Log.d(TAG, result);
                return result;
            }
            
            // FeliCa 
            FeliCaTag ft = new FeliCaTag(this.nfcTag);
            IDm idm = ft.pollingAndGetIDm(FeliCaLib.SYSTEMCODE_ANY);
            if ( idm != null ) {
                sb.append("\n");
                sb.append("FeliCa デバイスです");
                sb.append("\n-----------------------------------------");
                sb.append("\n");
                sb.append(ft.toString());

                // enum systemCode
                sb.append("\n");
                sb.append("  システムコード一覧");
                sb.append("\n  -----------------------------------------");
                sb.append("\n");
                SystemCode[] scs = ft.getSystemCodeList();
                for ( SystemCode sc : scs ) {
                    sb.append("  ").append(sc.toString()).append("\n");
                }

                // enum serviceCode
                sb.append("\n");
                sb.append("  サービスコード一覧");
                sb.append("\n-  ----------------------------------------");
                sb.append("\n");
                ServiceCode[] svs = ft.getServiceCodeList();
                for ( ServiceCode sc : svs ) {
                    sb.append("  ").append(sc.toString()).append("\n");
                }
            } else {
                sb.append("デバイスの読み込みに失敗しました");
            }
            
        } catch (Exception e) {
            String result = sb.toString();
            Log.d(TAG, result);
            e.printStackTrace();
            return result;
        }
        String result = sb.toString();
        Log.d(TAG, result);
        return result;
   }
}
