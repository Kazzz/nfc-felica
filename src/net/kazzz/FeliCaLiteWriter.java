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

import java.nio.charset.Charset;

import net.kazzz.felica.FeliCaException;
import net.kazzz.felica.FeliCaLiteTag;
import net.kazzz.felica.command.ReadResponse;
import net.kazzz.felica.command.WriteResponse;
import net.kazzz.felica.lib.FeliCaLib.IDm;
import net.kazzz.felica.lib.FeliCaLib.MemoryConfigurationBlock;
import net.kazzz.felica.lib.FeliCaLib.PMm;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * FeliCa Liteのデータを書きこむためのライタークラスのサンプル実装を提供します
 *
 * @author Kazzz
 * @date 2011/01/21
 * @since Android API Level 9
 *
 */

public class FeliCaLiteWriter extends Activity implements OnClickListener {
    private String TAG = "FeliCaLiteWriter";
    
    // ブロック番号の規定値 ( S_PAD0～13 )
    private final static String[] blockNums = new String[] {
            "S_PAD0", "S_PAD1", "S_PAD2", "S_PAD3", 
            "S_PAD4", "S_PAD5", "S_PAD6", "S_PAD7", 
            "S_PAD8", "S_PAD9", "S_PAD10", "S_PAD11", 
            "S_PAD12", "S_PAD13" 
        };
    
    /**
     * リストビューに表示するデータを保持するホルダクラスを提供します
     * 
     * @date 2011/02/22
     * @since Android API Level 9
     *
     */
    public class ViewHolder {
        String blockName;
        boolean isWritable;
        String accessMode;
        byte[] data;
    }
    
    private Parcelable nfcTag;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        //IMEを自動起動しない
        this.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        //レイアウトのインフレート
        setContentView(R.layout.felicalite);

        final Button btnWrite = (Button) findViewById(R.id.btn_write);
        btnWrite.setOnClickListener(this);
        btnWrite.setEnabled(false);

        final EditText editWrite = (EditText) findViewById(R.id.edit_write);
        editWrite.setEnabled(false);

        final ListView listMemBlock = (ListView) findViewById(R.id.list_memblock);

        Intent intent = getIntent();
        this.nfcTag = intent.getParcelableExtra("nfcTag");
        

        //データ読み込み
        ViewHolder[] holders = new ViewHolder[14];
        FeliCaLiteTag ft = new FeliCaLiteTag(this.nfcTag);
        try {
            IDm idm = ft.pollingAndGetIDm();
            if ( idm == null ) {
                throw new FeliCaException("FeliCa Lite デバイスからIDmを取得できませんでした");
            }
            //MemoryConfig 読み込み
            MemoryConfigurationBlock mb = ft.getMemoryConfigBlock(); 
            
            //スクラッチパッドのブロックを全て読み込んで配列に保存
            for ( byte i = 0; i < 14; i++) {
                ViewHolder holder = new ViewHolder();
                holder.blockName = blockNums[i];
                holder.isWritable = mb.isWritable(i);
                holder.accessMode = mb.isWritable(i) ? "(R/W)" : "(RO)";
                
                //メモリブロックの内容を取得
                ReadResponse rr = ft.readWithoutEncryption(i);
                if ( rr != null && rr.getStatusFlag1() == 0) {
                    holder.data = rr.getBlockData();
                }
                holders[i] = holder;
            }
        } catch (FeliCaException e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }
        final LayoutInflater layoutInflater = 
            (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        //ArrayAdapter構成 (ビューの編集をオーバライド
        ArrayAdapter<ViewHolder> adapter = 
            new ArrayAdapter<ViewHolder>(this, android.R.layout.simple_list_item_1, holders) {

                /* (non-Javadoc)
                 * @see android.widget.ArrayAdapter#getView(int, android.view.View, android.view.ViewGroup)
                 */
                @Override
                public View getView(int position, View convertView,
                        ViewGroup parent) {

                    if (null == convertView) {
                         convertView = 
                             layoutInflater.inflate(android.R.layout.simple_list_item_1, null);
                     }

                    ViewHolder holder = this.getItem(position);
                    
                    if ( holder != null ) {
                        
                        StringBuilder sb = new StringBuilder();
                        sb.append(holder.blockName).append(" ").append(holder.accessMode).append("\n");
                        if ( holder.data != null && holder.data.length > 0 ) {
                            //UTF-8でエンコード
                            Charset utfEncoding = Charset.forName("UTF-8");
                            sb.append("データ : ").append(new String(holder.data, utfEncoding).trim());
                        }
                        ((TextView)convertView).setText(sb.toString());
                    }
                    return convertView;
                }
            
        };
        
        
        listMemBlock.setAdapter(adapter);

        // 行が選択され際にデータをEditTextに転送
        listMemBlock.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                Object o = parent.getItemAtPosition(position);
                
                if ( o != null && o instanceof ViewHolder ) {
                    ViewHolder holder = (ViewHolder)o;
                    if ( holder.data != null && holder.data.length > 0 ) {
                        //UTF-8でエンコード
                        Charset utfEncoding = Charset.forName("UTF-8");
                        editWrite.setText( new String(holder.data, utfEncoding).trim() );
                        editWrite.setTag(position);
                    }

                    //書きこみ可能な場合、ビューを有効にするわさ
                    btnWrite.setEnabled(holder.isWritable); 
                    editWrite.setEnabled(holder.isWritable);
                } else {
                    btnWrite.setEnabled(false); 
                    editWrite.setEnabled(false);
                }

            }
        });
    }

    public void onClick(final View v) {
        try {
            final int id = v.getId();
            final ProgressDialog dialog = new ProgressDialog(this);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog.setIndeterminate(true);

            
            final EditText editWrite = (EditText) findViewById(R.id.edit_write);
            final CharSequence c = editWrite.getText();

            AsyncTask<Void, Void, WriteResponse> task = new AsyncTask<Void, Void, WriteResponse>() {
                @Override
                protected void onPreExecute() {
                    switch (id) {
                    case R.id.btn_write:
                        dialog.setMessage("書き込み処理を実行中です...");
                        break;
                    }
                    dialog.show();
                }
                @Override
                protected WriteResponse doInBackground(Void... arg0) {
                    switch (id) {
                    case R.id.btn_write:
                        byte addr = (byte) (((Integer)editWrite.getTag()) & 0xff);
                        try {
                            return writeData(addr, c);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } 
                    default:
                        break;
                    }
                    return null;
                }

                /* (non-Javadoc)
                 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
                 */
                @Override
                protected void onPostExecute(WriteResponse result) {
                    dialog.dismiss();
                    if ( result != null && result.getStatusFlag1() == 0) {
                        //tv_tag.setText(readData());
                        Toast.makeText(v.getContext()
                                , "書きこみ成功 : " + c.toString() , Toast.LENGTH_LONG).show();
                        
                        //終了して自身を起動 (リフレッシュ)
                        finish();
                        Intent intent = new Intent(FeliCaLiteWriter.this, FeliCaLiteWriter.class);
                        intent.putExtra("nfcTag", FeliCaLiteWriter.this.nfcTag);
                        startActivity(intent);
                    }
                }
                
            };
            
            task.execute();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * FeliCa データを書き込みます
     * @param addr 書きこむデータブロックのアドレス(ブロックナンバー)をセット
     * @param data 書きこむデータを文字列でセット
     * @return WriteResponse 書き込んだ結果が戻ります
     * @throws Exception
     */
    private WriteResponse writeData(byte addr, CharSequence data) throws Exception {
        try {
            FeliCaLiteTag f = new FeliCaLiteTag(this.nfcTag);
            IDm idm = f.pollingAndGetIDm();
            Log.d(TAG, idm.toString());
            
            PMm pmm = f.getPMm();
            Log.d(TAG, pmm.toString());
            
            //データをエンコード
            Charset utfEncoding = Charset.forName("UTF-8");
            byte[] textBytes = data.toString().getBytes(utfEncoding);

            WriteResponse result = f.writeWithoutEncryption(addr, textBytes);
            
            return result;
        } catch (FeliCaException e) {
            e.printStackTrace();
            Log.e(TAG, "writeData", e);
            throw e;
        }

   }
}
