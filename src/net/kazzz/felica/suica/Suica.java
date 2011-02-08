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
package net.kazzz.felica.suica;

import static net.kazzz.felica.suica.DBUtil.COLUMNS_IRUCA_STATIONCODE;
import static net.kazzz.felica.suica.DBUtil.COLUMNS_STATIONCODE;
import static net.kazzz.felica.suica.DBUtil.COLUMN_ID;
import static net.kazzz.felica.suica.DBUtil.TABLE_IRUCA_STATIONCODE;
import static net.kazzz.felica.suica.DBUtil.TABLE_STATIONCODE;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import net.kazzz.felica.lib.Util;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * Suica(PASMO)データ抽象化したクラスを提供します
 *
 * <pre>Suica/Pasmoのデータに関してはFeliCa Library - http://sourceforge.jp/projects/felicalib/wiki/suica </pre>
 * <pre>駅コードに関してはサイバネ駅コードデータベース - http://www.denno.net/SFCardFan/</pre>
 * <pre>これらを参考にさせて頂いております</pre>
 *
 * @author Kazzz
 * @date 2011/01/24
 * @since Android API Level 9
 *
 */

public class Suica {
    /**
     * 使用履歴を抽象化したクラスを提供します
     *
     * @author Kazzz
     * @date 2011/01/24
     * @since Android API Level 9
     *
     */
    public static class History {
        final byte[] data;
        Context context;
        /**
         * コンストラクタ
         * @param data データのバイト列(16バイト)をセット
         * @param context androidコンテキストをセット
         */
        public History(byte[] data, Context context) {
            this.data = data;
            this.context = context;
        }
        /**
         * 機器種別を取得します
         * @return String 機器種別が戻ります
         */
        public String getConsoleType() {
            return Suica.getConsoleType(this.data[0]);
        }
        /**
         * 処理種別を取得します
         * @return String 処理種別
         */
        public String getProcessType() {
            return Suica.getProcessType(this.data[1]);
        }
        /**
         * 残高を取得します
         * @return BigDecimal 残高が戻ります
         */
        public long getBalance() {
            return new Long(Util.toInt(new byte[]{this.data[11], this.data[10]}));
        }
        /**
         * 処理日付(出場日付)を取得します
         * @return byte[]
         */
        public Date getProccessDate() {
            int date = Util.toInt(new byte[]{this.data[4], this.data[5]});
            int yy = date >> 9;
            int mm = (date >> 5) & 0xf;
            int dd = date & 0x1f;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, 2000 + yy);
            c.set(Calendar.MONTH, mm-1);
            c.set(Calendar.DAY_OF_MONTH, dd);

            //物販だったら時間もセット
            if ( this.isProductSales() ) {
                int time = Util.toInt(new byte[]{this.data[6], this.data[7]});
                int hh = time >> 11;
                int min = (time >> 5) & 0x3f;
                c.set(Calendar.HOUR_OF_DAY, hh);
                c.set(Calendar.MINUTE, min);
            } else {
                c.set(Calendar.HOUR_OF_DAY, 0);
                c.set(Calendar.MINUTE, 0);
            }
            return c.getTime();
        }
        /**
         * 入場駅を取得します
         * @return String バスの場合、序数0に会社名、1停留所名が戻ります
         *  鉄道の場合、序数0に会社名、1に路線名、2に駅名が戻ります
         */
        public String[] getEntranceStation() {
            if (!this.isProductSales()) {
                if ( this.isByBus() ) {
                    //バス利用の場合
                    return getBusStop(Util.toInt(new byte[]{this.data[6], this.data[7]})
                            , Util.toInt(new byte[]{this.data[8], this.data[9]}));
                } else {
                    //鉄道利用の場合
                    return getStation(this.data[15], this.data[6], this.data[7]);
                }
            } else {
                return new String[]{"", "", ""};
            }
        }
        /**
         * 出場駅を取得します
         * @return String バスの場合、序数0に会社名、1停留所名が戻ります
         *  鉄道の場合、序数0に会社名、1に路線名、2に駅名が戻ります (バスの場合入場と同じ値となります)
         */
        public String[] getExitStation() {
            if (!this.isProductSales()) {
                if ( this.isByBus() ) {
                    //バス利用の場合
                    return getBusStop(Util.toInt(new byte[]{this.data[6], this.data[7]})
                            , Util.toInt(new byte[]{this.data[8], this.data[9]}));
                } else {
                    //鉄道利用の場合
                    return getStation(this.data[15], this.data[8], this.data[9]);
                }
            } else {
                return new String[]{"", "", ""};
            }
        }
        /**
         *  地区コード、線区コード、駅順コードから駅名を取得します
         * <pre>http://sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
         * @param regionCode 地区コードをセット
         * @param lineCode 線区コードをセット
         * @param statioCode 駅順コードをセット
         * @return 取得できた場合、序数0に会社名、1に路線名、2に駅名が戻ります
         */
        private String[] getStation(int regionCode, int lineCode, int statioCode) {

            int areaCode = regionCode & 0xff;
            DBUtil util = new DBUtil(this.context);
            try {
                SQLiteDatabase db = util.openDataBase();
                Cursor c = db.query(TABLE_STATIONCODE
                        , COLUMNS_STATIONCODE
                        ,   COLUMNS_STATIONCODE[0] + " = '" + areaCode + "' and "
                          + COLUMNS_STATIONCODE[1] + " = '" + (lineCode & 0xff) + "' and "
                          + COLUMNS_STATIONCODE[2] + " = '" + (statioCode & 0xff) + "'"
                        , null, null, null, COLUMN_ID);

                return ( c.moveToFirst() )
                    ?  new String[]{ c.getString(3), c.getString(4), c.getString(5)}
                    :  new String[]{"???", "???", "???"};
            } catch (Exception e) {
                e.printStackTrace();
                return new String[]{"error", "error", "error"};
            } finally {
                util.close();
            }
        }
        /**
         * パス停留所を取得します
         * <pre>http://sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
         * @param lineCode 線区コードをセット
         * @param statioCode 駅順コードをセット
         * @return 取得できた場合、序数0に会社名、1停留所名が戻ります
         */
        private String[] getBusStop(int lineCode, int statioCode) {
            DBUtil util = new DBUtil(this.context);
            try {
                SQLiteDatabase db = util.openDataBase();
                Cursor c = db.query(TABLE_IRUCA_STATIONCODE
                        , COLUMNS_IRUCA_STATIONCODE
                        ,   COLUMNS_IRUCA_STATIONCODE[0] + " = '" + lineCode + "' and "
                          + COLUMNS_IRUCA_STATIONCODE[1] + " = '" + statioCode + "'"
                        , null, null, null, COLUMN_ID);
                return ( c.moveToFirst()  )
                    ?  new String[]{c.getString(2), c.getString(4)}
                    :  new String[]{"???", "???"};
            } catch (Exception e) {
                e.printStackTrace();
                return new String[]{"error", "error"};
            } finally {
                util.close();
            }
        }

        /**
         * 処理種別がバス利用か否かを検査します
         * <pre>http://sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
         * @return boolean バス利用の場合trueが戻ります
         */
        public boolean isByBus() {
            //data[0]端末種別が 車載の場合
            return (this.data[0] & 0xff) == 0x05;
        }
        /**
         *　端末種別が「物販」か否かを判定します
         * <pre>http://sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
         * @return boolean 物販だった場合はtrueが戻ります
         */
        public boolean isProductSales() {
            //data[0]端末種別が物販又は自販機
            return (this.data[0] & 0xff) == 0xc7
                || (this.data[0] & 0xff) == 0xc8;
        }
        /**
         *　処理種別が「チャージ」か否かを判定します (店舗名を取得できるか否かを判定します)
         * <pre>http://sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
         * @return boolean チャージだった場合はtrueが戻ります
         */
        public boolean isCharge() {
            return ( this.data[1] & 0xff) == 0x02;
        }

        /* (non-Javadoc)
         * @see java.lang.Object#toString()
         */
        @Override
        public String toString() {
            NumberFormat nf = NumberFormat.getCurrencyInstance();
            nf.setMaximumFractionDigits(0);
            SimpleDateFormat dfl = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            SimpleDateFormat dfs = new SimpleDateFormat("yyyy/MM/dd");

            StringBuilder sb = new StringBuilder();
            sb.append("機器種別: " + this.getConsoleType() + "\n");
            sb.append("処理種別: " + this.getProcessType() + "\n");
            if ( !this.isProductSales() ) {
                sb.append("処理日付: " + dfs.format(this.getProccessDate()) + "\n");
                if ( this.isByBus() ) {
                    String[] busStopInfo = this.getEntranceStation();
                    sb.append("利用会社: " + busStopInfo[0]);
                    sb.append("停留所: " + busStopInfo[1] + "\n");
                } else {
                    String[] entranceInfo = this.getEntranceStation();
                    String[] exitInfo = this.getExitStation();

                    sb.append("入場: " + "\n");
                    sb.append("  利用会社: " + entranceInfo[0]+ "\n");
                    sb.append("  路線名: " + entranceInfo[1]+ "線\n");
                    sb.append("  駅名: " + entranceInfo[2] + "\n");

                    if ( !this.isCharge()) {
                        sb.append("出場: " + "\n");
                        sb.append("  利用会社: " + exitInfo[0]+ "\n");
                        sb.append("  路線名: " + exitInfo[1]+ "線\n");
                        sb.append("  駅名: " + exitInfo[2] + "\n");
                    }
                }
            } else {
                sb.append("処理日付: " + dfl.format(this.getProccessDate()) + "\n");
            }
            //sb.append("支払種別: " + this.getPaymentType() + "\n");
            sb.append("残高: " + nf.format(this.getBalance()) + "\n");
            return sb.toString();
       }



    }
    /**
     * 機器種別を取得します
     * <pre>http://sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
     * @param cType コンソールタイプをセット
     * @return String 機器タイプが文字列で戻ります
     */
    public static final String getConsoleType(int cType) {
        switch (cType & 0xff) {
            case 0x03: return "精算機";
            case 0x04: return "携帯型端末";
            case 0x05: return "等車載端末"; //bus
            case 0x07: return "券売機";
            case 0x08: return "券売機";
            case 0x09: return "入金機(クイックチャージ機)";
            case 0x12: return "券売機(東京モノレール)";
            case 0x13: return "券売機等";
            case 0x14: return "券売機等";
            case 0x15: return "券売機等";
            case 0x16: return "改札機";
            case 0x17: return "簡易改札機";
            case 0x18: return "窓口端末";
            case 0x19: return "窓口端末(みどりの窓口)";
            case 0x1a: return "改札端末";
            case 0x1b: return "携帯電話";
            case 0x1c: return "乗継清算機";
            case 0x1d: return "連絡改札機";
            case 0x1f: return "簡易入金機";
            case 0x46: return "VIEW ALTTE";
            case 0x48: return "VIEW ALTTE";
            case 0xc7: return "物販端末";  //sales
            case 0xc8: return "自販機";   //sales
            default:
                return "???";
        }
    }
    /**
     * 処理種別を取得します
     * <pre>http://sourceforge.jp/projects/felicalib/wiki/suicaを参考にしています</pre>
     * @param proc 処理タイプをセット
     * @return String 処理タイプが文字列で戻ります
     */
    public static final String getProcessType(int proc) {
        switch (proc & 0xff) {
            case 0x01: return "運賃支払(改札出場)";
            case 0x02: return "チャージ";
            case 0x03: return "券購(磁気券購入";
            case 0x04: return "精算";
            case 0x05: return "精算(入場精算)";
            case 0x06: return "窓出(改札窓口処理)";
            case 0x07: return "新規(新規発行)";
            case 0x08: return "控除(窓口控除)";
            case 0x0d: return "バス(PiTaPa系)";    //byBus
            case 0x0f: return "バス(IruCa系)";     //byBus
            case 0x11: return "再発(再発行処理)";
            case 0x13: return "支払(新幹線利用)";
            case 0x14: return "入A(入場時オートチャージ)";
            case 0x15: return "出A(出場時オートチャージ)";
            case 0x1f: return "入金(バスチャージ)";            //byBus
            case 0x23: return "券購 (バス路面電車企画券購入)";  //byBus
            case 0x46: return "物販";                 //sales
            case 0x48: return "特典(特典チャージ)";
            case 0x49: return "入金(レジ入金)";         //sales
            case 0x4a: return "物販取消";              //sales
            case 0x4b: return "入物 (入場物販)";        //sales
            case 0xc6: return "物現 (現金併用物販)";     //sales
            case 0xcb: return "入物 (入場現金併用物販)"; //sales
            case 0x84: return "精算 (他社精算)";
            case 0x85: return "精算 (他社入場精算)";
            default:
                return "???";
        }
    }

}
