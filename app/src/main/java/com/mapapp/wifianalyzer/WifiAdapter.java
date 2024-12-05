package com.mapapp.wifianalyzer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class WifiAdapter extends RecyclerView.Adapter<WifiAdapter.WifiViewHolder> {

    private List<String> wifiList;  // Wi-Fi 네트워크 정보를 담을 리스트

    public WifiAdapter(List<String> wifiList) {
        this.wifiList = wifiList;
    }

    @NonNull
    @Override
    public WifiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wifi_item, parent, false);
        return new WifiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WifiViewHolder holder, int position) {
        String wifiData = wifiList.get(position);
        holder.wifiInfoTextView.setText(wifiData);
    }

    @Override
    public int getItemCount() {
        return wifiList.size();
    }

    public static class WifiViewHolder extends RecyclerView.ViewHolder {
        TextView wifiInfoTextView;

        public WifiViewHolder(@NonNull View itemView) {
            super(itemView);
            wifiInfoTextView = itemView.findViewById(R.id.wifiInfoTextView);
        }
    }

    // Wi-Fi 리스트 갱신 메서드
    public void updateWiFiList(List<String> newList) {
        wifiList = newList;
        notifyDataSetChanged();  // 데이터 갱신
    }
}
