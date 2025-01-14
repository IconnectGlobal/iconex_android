package foundation.icon.iconex.menu.appInfo;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import foundation.icon.iconex.R;

public class OSSFragment extends Fragment {

    public OSSFragment() {
        // Required empty public constructor
    }

    public static OSSFragment newInstance() {
        OSSFragment fragment = new OSSFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_oss, container, false);

        WebView webView = v.findViewById(R.id.webview);
        webView.loadUrl("file:///android_asset/open_source_licenses.html");

        return v;
    }
}
