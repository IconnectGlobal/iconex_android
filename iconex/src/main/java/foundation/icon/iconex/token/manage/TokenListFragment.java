package foundation.icon.iconex.token.manage;

import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

import foundation.icon.ICONexApp;
import foundation.icon.MyConstants;
import foundation.icon.iconex.R;
import foundation.icon.iconex.wallet.Wallet;
import foundation.icon.iconex.wallet.WalletEntry;

public class TokenListFragment extends Fragment {

    private static final String TAG = TokenListFragment.class.getSimpleName();

    private static final String ARG_WALLET = "ARG_WALLET";

    private Wallet mWallet;
    private List<WalletEntry> mTokens;

    private ViewGroup layoutNoTokens;
    private RecyclerView recyclerToken;
    private TokenListAdapter adapter;

    private Button btnAdd;

    public TokenListFragment() {
        // Required empty public constructor
    }

    public static TokenListFragment newInstance(Wallet wallet) {
        TokenListFragment fragment = new TokenListFragment();
        Bundle bundle = new Bundle();
        bundle.putSerializable(ARG_WALLET, wallet);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mWallet = (Wallet) getArguments().get(ARG_WALLET);
        }

        mTokens = makeTokenList();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_token_list, container, false);

        layoutNoTokens = v.findViewById(R.id.layout_no_tokens);

        if (mTokens.size() == 0)
            layoutNoTokens.setVisibility(View.VISIBLE);
        else
            layoutNoTokens.setVisibility(View.GONE);

        recyclerToken = v.findViewById(R.id.recycler_token);

        btnAdd = v.findViewById(R.id.btn_add_token);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null)
                    mListener.onTokenAdd();
            }
        });

        refreshTokenList();
        adapter = new TokenListAdapter(getActivity(), mTokens);
        adapter.setOnItemClickListener(new TokenListAdapter.OnTokenClickListener() {
            @Override
            public void onItemClick(WalletEntry token) {
                mListener.onTokenClick(token);
            }
        });
        recyclerToken.setAdapter(adapter);

        return v;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnTokenListClickListener) {
            mListener = (OnTokenListClickListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnStep1Listener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private List<WalletEntry> makeTokenList() {
        List<WalletEntry> tokens = new ArrayList<>();
        for (WalletEntry entry : mWallet.getWalletEntries()) {
            if (entry.getType().equals(MyConstants.TYPE_TOKEN))
                tokens.add(entry);
        }

        return tokens;
    }

    private void refreshTokenList() {

        mTokens = new ArrayList<>();

        for (Wallet info : ICONexApp.mWallets) {
            if (info.getAddress().equals(mWallet.getAddress())) {
                for (WalletEntry entry : info.getWalletEntries()) {
                    if (entry.getType().equals(MyConstants.TYPE_TOKEN)) {
                        mTokens.add(entry);
                    }
                }
            }
        }
    }

    public void tokenNotifyDataChanged() {
        refreshTokenList();
        if (mTokens.size() > 0)
            layoutNoTokens.setVisibility(View.GONE);
        else
            layoutNoTokens.setVisibility(View.VISIBLE);

        adapter = new TokenListAdapter(getActivity(), mTokens);
        adapter.setOnItemClickListener(new TokenListAdapter.OnTokenClickListener() {
            @Override
            public void onItemClick(WalletEntry token) {
                mListener.onTokenClick(token);
            }
        });
        recyclerToken.setAdapter(adapter);
    }

    private OnTokenListClickListener mListener = null;

    public interface OnTokenListClickListener {
        void onTokenClick(WalletEntry entry);

        void onTokenAdd();
    }
}
