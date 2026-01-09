package com.example.akn_callai;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CallLogAdapter extends RecyclerView.Adapter<CallLogAdapter.CallViewHolder> {

    // This class represents ONE single row of data
    public static class CallLogItem {
        String name;
        String number;
        String time;
        boolean handledByAI;

        public CallLogItem(String name, String number, String time, boolean handledByAI) {
            this.name = name;
            this.number = number;
            this.time = time;
            this.handledByAI = handledByAI;
        }
    }

    private List<CallLogItem> callList;

    public CallLogAdapter(List<CallLogItem> callList) {
        this.callList = callList;
    }

    @NonNull
    @Override
    public CallViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the XML layout we created earlier
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.call_log_item, parent, false);
        return new CallViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CallViewHolder holder, int position) {
        CallLogItem currentItem = callList.get(position);

        holder.tvName.setText(currentItem.name);
        holder.tvNumber.setText(currentItem.number);
        holder.tvTime.setText(currentItem.time);

        if (!currentItem.name.isEmpty()) {
            holder.tvAvatar.setText(String.valueOf(currentItem.name.charAt(0)));
        }

        if (currentItem.handledByAI) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText("AI REPLIED");
            holder.tvStatus.setBackgroundColor(0xFF4CAF50);
        } else {
            holder.tvStatus.setVisibility(View.GONE);
        }

        // --- NEW: CLICK TO CALL LOGIC ---
        holder.itemView.setOnClickListener(v -> {
            String phone = currentItem.number;
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + phone));
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return callList.size();
    }

    // The inner class that holds the View IDs
    public static class CallViewHolder extends RecyclerView.ViewHolder {
        public TextView tvName, tvNumber, tvTime, tvStatus, tvAvatar;

        public CallViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvNumber = itemView.findViewById(R.id.tvNumber);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvStatus = itemView.findViewById(R.id.tvStatusBadge);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
        }
    }
}
