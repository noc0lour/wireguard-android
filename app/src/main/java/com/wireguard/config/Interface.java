package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Parcel;
import android.os.Parcelable;

import com.wireguard.android.BR;
import com.wireguard.crypto.KeyEncoding;
import com.wireguard.crypto.Keypair;

/**
 * Represents the configuration for a WireGuard interface (an [Interface] block).
 */

public class Interface extends BaseObservable implements Parcelable {
    public static final Creator<Interface> CREATOR = new Creator<Interface>() {
        @Override
        public Interface createFromParcel(final Parcel in) {
            return new Interface(in);
        }

        @Override
        public Interface[] newArray(final int size) {
            return new Interface[size];
        }
    };

    private String address;
    private String dns;
    private Keypair keypair;
    private String listenPort;
    private String mtu;
    private String privateKey;

    public Interface() {
        // Do nothing.
    }

    private Interface(final Parcel in) {
        address = in.readString();
        dns = in.readString();
        listenPort = in.readString();
        mtu = in.readString();
        setPrivateKey(in.readString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void generateKeypair() {
        keypair = new Keypair();
        privateKey = keypair.getPrivateKey();
        notifyPropertyChanged(BR.privateKey);
        notifyPropertyChanged(BR.publicKey);
    }

    @Bindable
    public String getAddress() {
        return address;
    }

    @Bindable
    public String getDns() {
        return dns;
    }

    @Bindable
    public String getListenPort() {
        return listenPort;
    }

    @Bindable
    public String getMtu() {
        return mtu;
    }

    @Bindable
    public String getPrivateKey() {
        return privateKey;
    }

    @Bindable
    public String getPublicKey() {
        return keypair != null ? keypair.getPublicKey() : null;
    }

    public void parse(final String line) {
        final Attribute key = Attribute.match(line);
        if (key == Attribute.ADDRESS)
            setAddress(key.parse(line));
        else if (key == Attribute.DNS)
            setDns(key.parse(line));
        else if (key == Attribute.LISTEN_PORT)
            setListenPort(key.parse(line));
        else if (key == Attribute.MTU)
            setMtu(key.parse(line));
        else if (key == Attribute.PRIVATE_KEY)
            setPrivateKey(key.parse(line));
        else
            throw new IllegalArgumentException(line);
    }

    public void setAddress(String address) {
        if (address != null && address.isEmpty())
            address = null;
        this.address = address;
        notifyPropertyChanged(BR.address);
    }

    public void setDns(String dns) {
        if (dns != null && dns.isEmpty())
            dns = null;
        this.dns = dns;
        notifyPropertyChanged(BR.dns);
    }

    public void setListenPort(String listenPort) {
        if (listenPort != null && listenPort.isEmpty())
            listenPort = null;
        this.listenPort = listenPort;
        notifyPropertyChanged(BR.listenPort);
    }

    public void setMtu(String mtu) {
        if (mtu != null && mtu.isEmpty())
            mtu = null;
        this.mtu = mtu;
        notifyPropertyChanged(BR.mtu);
    }

    public void setPrivateKey(String privateKey) {
        if (privateKey != null && privateKey.isEmpty())
            privateKey = null;
        this.privateKey = privateKey;
        if (privateKey != null && privateKey.length() == KeyEncoding.KEY_LENGTH_BASE64) {
            try {
                keypair = new Keypair(privateKey);
            } catch (final IllegalArgumentException ignored) {
                keypair = null;
            }
        } else {
            keypair = null;
        }
        notifyPropertyChanged(BR.privateKey);
        notifyPropertyChanged(BR.publicKey);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append("[Interface]\n");
        if (address != null)
            sb.append(Attribute.ADDRESS.composeWith(address));
        if (dns != null)
            sb.append(Attribute.DNS.composeWith(dns));
        if (listenPort != null)
            sb.append(Attribute.LISTEN_PORT.composeWith(listenPort));
        if (mtu != null)
            sb.append(Attribute.MTU.composeWith(mtu));
        if (privateKey != null)
            sb.append(Attribute.PRIVATE_KEY.composeWith(privateKey));
        return sb.toString();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(address);
        dest.writeString(dns);
        dest.writeString(listenPort);
        dest.writeString(mtu);
        dest.writeString(privateKey);
    }
}
