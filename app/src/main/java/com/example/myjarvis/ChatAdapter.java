package com.example.myjarvis;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final Context context;
    private final List<ChatMessage> messageList;

    public ChatAdapter(Context context, List<ChatMessage> messageList) {
        this.context = context;
        this.messageList = messageList;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);
        holder.tvMessage.setText(message.getMessage());
        holder.tvTime.setText(message.getTime());

        if (message.isUser()) {
            holder.messageContainer.setGravity(Gravity.END);
            holder.bubbleLayout.setBackgroundResource(R.drawable.bg_bubble_user);
            holder.tvMessage.setTextColor(ContextCompat.getColor(context,
                    R.color.lira_user_bubble_text));
            holder.tvTime.setTextColor(ContextCompat.getColor(context, R.color.lira_user_time));
        } else {
            holder.messageContainer.setGravity(Gravity.START);
            holder.bubbleLayout.setBackgroundResource(R.drawable.bg_bubble_assistant);
            holder.tvMessage.setTextColor(ContextCompat.getColor(context, R.color.lira_text));
            holder.tvTime.setTextColor(ContextCompat.getColor(context,
                    R.color.lira_assistant_time));
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {
        LinearLayout messageContainer, bubbleLayout;
        TextView tvMessage, tvTime;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            bubbleLayout = itemView.findViewById(R.id.bubbleLayout);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}
