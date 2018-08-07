package foundation.icon.iconex.service;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.spongycastle.util.encoders.Hex;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jFactory;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response.Error;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Convert;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import ethereum.contract.MyContract;
import ethereum.contract.MyTransactionManager;
import foundation.icon.iconex.ICONexApp;
import foundation.icon.iconex.MyConstants;
import foundation.icon.iconex.util.ConvertUtil;
import loopchain.icon.wallet.core.Constants;
import loopchain.icon.wallet.core.response.LCResponse;
import loopchain.icon.wallet.core.response.TRResponse;
import loopchain.icon.wallet.service.LoopChainClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static foundation.icon.iconex.ICONexApp.isMain;

public class NetworkService extends Service {

    private static final String TAG = NetworkService.class.getSimpleName();

    private HashMap<String, AsyncTask> ethMap;
    private HashMap<String, Thread> icxMap;

    private final IBinder mBinder = new NetworkServiceBinder();

    public NetworkService() {
    }

    public class NetworkServiceBinder extends Binder {
        public NetworkService getService() {
            return NetworkService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        ethMap = new HashMap<>();
        icxMap = new HashMap<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    public void stopGetBalance() {
        for (Map.Entry entry : ethMap.entrySet()) {
            String key = (String) entry.getKey();
            ethMap.get(key).cancel(true);
        }

        for (Map.Entry entry : icxMap.entrySet()) {
            String key = (String) entry.getKey();
            icxMap.get(key).interrupt();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void requestGetBalance(HashMap<String, String> addresses, String coinType) {
        for (Map.Entry<String, String> entry : addresses.entrySet()) {
            String id = entry.getKey();
            String address = entry.getValue();

            if (coinType.equals(Constants.KS_COINTYPE_ICX))
                getICXBalance(id, address);
            else
                getETHBalance(id, address);
        }
    }

    public void requestGetTokenBalance(HashMap<String, String[]> addresses) {
        for (Map.Entry<String, String[]> entry : addresses.entrySet()) {
            String id = entry.getKey();
            String[] values = entry.getValue();
            String ownAddress = values[0];
            String contactAddress = values[1];

            getTokenBalance(id, ownAddress, contactAddress);
        }
    }

    private void getICXBalance(final String id, final String address) {

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url;
                    if (isMain)
                        url = ServiceConstants.TRUSTED_HOST_MAIN;
                    else
                        url = ServiceConstants.TRUSTED_HOST_TEST;

                    LoopChainClient client = new LoopChainClient(url);
                    Call<LCResponse> responseCall = client.getBalance(id, address);
                    responseCall.enqueue(new Callback<LCResponse>() {
                        @Override
                        public void onResponse(Call<LCResponse> call, Response<LCResponse> response) {
                            if (response.isSuccessful()) {
                                int resCode = response.body().getResult().getAsJsonObject().get("response_code").getAsInt();
                                // TODO: 2018. 3. 19. null
                                if (resCode == MyConstants.CODE_OK) {
                                    String id = response.body().getID();
                                    String hexBalance = response.body().getResult().getAsJsonObject().get("response").getAsString();
                                    String balance = ConvertUtil.hexStringToBigInt(hexBalance, 18).toString();

                                    mBalanceCallback.onReceiveICXBalance(id, address, balance);
                                } else {
                                    mBalanceCallback.onReceiveError(id, address, resCode);
                                }
                            } else {
                                mBalanceCallback.onReceiveError(id, address, response.code());
                            }
                        }

                        @Override
                        public void onFailure(Call<LCResponse> call, Throwable t) {
                            mBalanceCallback.onReceiveException(id, address, t.getMessage());
                        }
                    });
                } catch (Exception e) {
                    mBalanceCallback.onReceiveException(id, address, e.getMessage());
                } finally {
                    icxMap.remove(id);
                }
            }
        });
        icxMap.put(id, thread);
        thread.start();
    }

    private void getETHBalance(final String id, final String address) {
        ETHBalance getBalance = new ETHBalance();
        ethMap.put(id, getBalance);
        getBalance.execute(id, address);
    }

    private void getTokenBalance(final String id, final String ownAddress, final String contractAddress) {
        TokenBalance getBalance = new TokenBalance();
        ethMap.put(id, getBalance);
        getBalance.execute(id, ownAddress, contractAddress);
    }

    public void requestExchangeList(final String exchangeList) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url;
                    if (isMain)
                        url = ServiceConstants.TRUSTED_TRACKER_MAIN;
                    else
                        url = ServiceConstants.TRUSTED_TRACKER_TEST;

                    LoopChainClient client = new LoopChainClient(url);
                    Call<TRResponse> responseCall = client.getExchangeRates(exchangeList);
                    responseCall.enqueue(new Callback<TRResponse>() {
                        @Override
                        public void onResponse(Call<TRResponse> call, Response<TRResponse> response) {
                            int code = response.code();
                            if (code == 200) {
                                String result = response.body().getResult();
                                if (result.equals(MyConstants.RESULT_OK)) {
                                    JsonElement data = response.body().getData();
                                    JsonArray list = data.getAsJsonArray();
                                    for (int i = 0; i < list.size(); i++) {
                                        JsonObject item = list.get(i).getAsJsonObject();
                                        String tradeName = item.get("tradeName").getAsString();
                                        String price = item.get("price").getAsString();
                                        ICONexApp.EXCHANGE_TABLE.put(tradeName, price);
                                    }

                                    mExchangeCallback.onReceiveExchangeList();
                                } else {
                                    mExchangeCallback.onReceiveError(result);
                                }
                            } else {
                                mExchangeCallback.onReceiveError(String.valueOf(code));
                            }
                        }

                        @Override
                        public void onFailure(Call<TRResponse> call, Throwable t) {
                            mExchangeCallback.onReceiveException(t);
                        }
                    });
                } catch (Exception e) {
                    mExchangeCallback.onReceiveException(e);
                }
            }
        }).start();
    }

    public void requestICONTxList(final String address, final int page) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url;
                    if (isMain)
                        url = ServiceConstants.TRUSTED_TRACKER_MAIN;
                    else
                        url = ServiceConstants.TRUSTED_TRACKER_TEST;

                    LoopChainClient client = new LoopChainClient(url);
                    Call<TRResponse> responseCall = client.getTxList(address, page);
                    responseCall.enqueue(new Callback<TRResponse>() {
                        @Override
                        public void onResponse(Call<TRResponse> call, Response<TRResponse> response) {
                            String resCode = response.body().getResult();
                            if (resCode.equals(MyConstants.RESULT_OK)) {
                                String totalData = response.body().getTotalData();
                                JsonObject data = response.body().getData().getAsJsonObject();
                                JsonArray txList = data.get("walletTx").getAsJsonArray();
                                mTxListCallback.onReceiveTransactionList(Integer.parseInt(totalData), txList);
                            } else {
                                mTxListCallback.onReceiveError(resCode);
                            }
                        }

                        @Override
                        public void onFailure(Call<TRResponse> call, Throwable t) {
                            mTxListCallback.onReceiveException(t);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    mTxListCallback.onReceiveException(e);
                }
            }
        }).start();
    }

    public void requestICXTransaction(final String id, final String timestamp, final String from, final String to, final String value, final String fee, final String privateKey) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url;
                    if (isMain)
                        url = ServiceConstants.TRUSTED_HOST_MAIN;
                    else
                        url = ServiceConstants.TRUSTED_HOST_TEST;

                    LoopChainClient client = new LoopChainClient(url);
                    Call<LCResponse> responseCall = client.sendTransaction(id, timestamp, from, to, value, fee, privateKey);
                    responseCall.enqueue(new Callback<LCResponse>() {
                        @Override
                        public void onResponse(Call<LCResponse> call, Response<LCResponse> response) {
                            JsonElement result = response.body().getResult();
                            if (result != null) {
                                int resCode = result.getAsJsonObject().get("response_code").getAsInt();
                                if (resCode == 0) {
                                    String txHash = result.getAsJsonObject().get("tx_hash").getAsString();
                                    mRemCallback.onReceiveTransactionResult(id, txHash);
                                } else {
                                    mRemCallback.onReceiveError(from, resCode);
                                }
                            } else {
                                mRemCallback.onReceiveError(from, 9999);
                            }
                        }

                        @Override
                        public void onFailure(Call<LCResponse> call, Throwable t) {

                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    mRemCallback.onReceiveException(e);
                }
            }
        }).start();
    }

    public void requestETHTransaction(String id, String price, String limit, String to, String data, String value, String privateKey) {
        ETHTransaction request = new ETHTransaction();
        request.execute(id, price, limit, to, data, value, privateKey);
    }

    public void requestTokenTransfer(String id, String price, String limit, String contract, String to, String value, String radix, String privateKey) {
        TokenTransfer transfer = new TokenTransfer();
        transfer.execute(id, price, limit, contract, to, value, radix, privateKey);
    }

    private BalanceCallback mBalanceCallback;
    private TxListCallback mTxListCallback;
    private ExchangeCallback mExchangeCallback;
    private RemittanceCallback mRemCallback;

    public void registerBalanceCallback(BalanceCallback callback) {
        mBalanceCallback = callback;
    }

    public void registerTxListCallback(TxListCallback callback) {
        mTxListCallback = callback;
    }

    public void registerExchangeCallback(ExchangeCallback callback) {
        mExchangeCallback = callback;
    }

    public void registerRemCallback(RemittanceCallback callback) {
        mRemCallback = callback;
    }

    public interface BalanceCallback {
        void onReceiveICXBalance(String id, String address, String result);

        void onReceiveETHBalance(String id, String address, String result);

        void onReceiveTokenBalance(String id, String address, String result);

        void onReceiveError(String id, String address, int code);

        void onReceiveException(String id, String address, String msg);
    }

    public interface TxListCallback {
        void onReceiveTransactionList(int totalData, JsonArray txList);

        void onReceiveError(String resCode);

        void onReceiveException(Throwable t);
    }

    public interface ExchangeCallback {
        void onReceiveExchangeList();

        void onReceiveError(String resCode);

        void onReceiveException(Throwable t);
    }

    public interface RemittanceCallback {
        void onReceiveTransactionResult(String id, String txHash);

        void onReceiveError(String address, int code);

        void onReceiveException(Throwable t);
    }

    private class ETHBalance extends AsyncTask<String, Void, String[]> {
        String id;
        String address;

        @Override
        protected String[] doInBackground(String... params) {

            id = params[0];
            address = params[1];

            String url;
            if (isMain)
                url = ServiceConstants.ETH_HOST;
            else
                url = ServiceConstants.ETH_ROP_HOST;

            try {
                Web3j web3j = Web3jFactory.build(new HttpService(url));
                EthGetBalance getBalance = web3j.ethGetBalance(params[1], DefaultBlockParameterName.LATEST).sendAsync().get();
                Error error = getBalance.getError();
                if (error == null) {
                    return new String[]{params[0], params[1], getBalance.getBalance().toString()};
                } else {
                    int errCode = error.getCode();
                    mBalanceCallback.onReceiveError(id, address, errCode);
                }
            } catch (Exception e) {
                return null;
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] s) {
            super.onPostExecute(s);

            if (s == null)
                mBalanceCallback.onReceiveError(id, address, 8888);
            else
                mBalanceCallback.onReceiveETHBalance(s[0], s[1], s[2]);

            ethMap.remove(id);
        }
    }

    private class ETHTransaction extends AsyncTask<String, Void, String[]> {
        @Override
        protected String[] doInBackground(String... params) {
            String url;
            if (isMain)
                url = ServiceConstants.ETH_HOST;
            else
                url = ServiceConstants.ETH_ROP_HOST;

            Web3j web3j = Web3jFactory.build(new HttpService(url));

            try {
                String id = params[0];
                String price = params[1];
                String limit = params[2];
                String to = params[3];
                String data = params[4];
                String value = params[5];
                String privKey = params[6];

                ECKeyPair keyPair = ECKeyPair.create(Hex.decode(privKey));
                Credentials credentials = Credentials.create(keyPair);
                RawTransactionManager rawTransactionManager = new RawTransactionManager(web3j, credentials);
                EthSendTransaction tx = rawTransactionManager.sendTransaction(
                        Convert.toWei(price, Convert.Unit.GWEI).toBigInteger(),
                        new BigInteger(limit),
                        to,
                        data,
                        LoopChainClient.valueToBigInteger(value));
                if (tx.hasError()) {
//                    Log.d(TAG, "Has error = " + tx.getError().getMessage());
                }
//                Log.d(TAG, "txHash=" + tx.getTransactionHash());

                return new String[]{id, tx.getTransactionHash()};

            } catch (Exception e) {
                e.printStackTrace();

                return null;
            }
        }

        @Override
        protected void onPostExecute(String[] result) {
            super.onPostExecute(result);

//            if (result != null) {
//                mRemCallback.onReceiveTransactionResult(result[0], result[1]);
//            } else {
//                mRemCallback.onReceiveError("ETHTransfer", 9999);
//            }
        }
    }

    private class TokenBalance extends AsyncTask<String, Void, String[]> {
        String id;
        String own;
        String contract;

        @Override
        protected String[] doInBackground(String... params) {

            id = params[0];
            own = params[1];
            contract = params[2];

            String url;
            if (isMain)
                url = ServiceConstants.ETH_HOST;
            else
                url = ServiceConstants.ETH_ROP_HOST;

            Web3j web3j = Web3jFactory.build(new HttpService(url));

            try {
                MyTransactionManager txManager = new MyTransactionManager(web3j, contract, Collections.EMPTY_LIST);
                MyContract myContract = MyContract.load(contract, web3j, txManager, Contract.GAS_PRICE, Contract.GAS_LIMIT);
                BigInteger balance = myContract.balanceOf(own).send();

                return new String[]{id, own, balance.toString()};
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String[] result) {
            super.onPostExecute(result);

            if (result == null)
                mBalanceCallback.onReceiveError(id, own, 9999);
            else
                mBalanceCallback.onReceiveTokenBalance(result[0], result[1], result[2]);

            ethMap.remove(id);
        }
    }

    private class TokenTransfer extends AsyncTask<String, Void, String[]> {
        @Override
        protected String[] doInBackground(String... params) {

            String id = params[0];
            String price = params[1];
            String limit = params[2];
            String contract = params[3];
            String to = params[4];
            String value = params[5];
            int decimals = Integer.parseInt(params[6]);
            String privKey = params[7];

            String url;
            if (isMain)
                url = ServiceConstants.ETH_HOST;
            else
                url = ServiceConstants.ETH_ROP_HOST;

            try {
                Web3j web3j = Web3jFactory.build(new HttpService(url));

                ECKeyPair keyPair = ECKeyPair.create(Hex.decode(privKey));
                Credentials credentials = Credentials.create(keyPair);

                MyContract myContract = MyContract.load(contract, web3j, credentials,
                        Convert.toWei(price, Convert.Unit.GWEI).toBigInteger(), new BigInteger(limit));
                TransactionReceipt receipt = myContract.transfer(to, ConvertUtil.valueToBigInteger(value, decimals)).send();

                return new String[]{id, receipt.getTransactionHash()};

            } catch (Exception e) {
                e.printStackTrace();

                return null;
            }
        }

        @Override
        protected void onPostExecute(String[] results) {
            super.onPostExecute(results);

//            if (results != null) {
//                mRemCallback.onReceiveTransactionResult(results[0], results[1]);
//            } else {
//                mRemCallback.onReceiveError("TokenTransfer", 8888);
//            }
        }
    }
}
