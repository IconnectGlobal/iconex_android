package foundation.icon.iconex.wallet.menu;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

/**
 * Created by js on 2018. 3. 21..
 */

public class WalletInfoViewPager extends FragmentStatePagerAdapter {

    private final int PAGE_COUNT = 2;

    private String mAddress;
    private String mPrivKey;

    public WalletInfoViewPager(FragmentManager fm, String address, String privKey) {
        super(fm);

        mAddress = address;
        mPrivKey = privKey;
    }

    @Override
    public Fragment getItem(int position) {
        if (position == 0) {
            QRAddressFragment addressFragment = QRAddressFragment.newInstance(mAddress);
            return addressFragment;
        } else {
            QRPrivateKeyFragment privateKeyFragment = QRPrivateKeyFragment.newInstance(mPrivKey);
            return privateKeyFragment;
        }
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }
}
