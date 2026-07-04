package com.cloudx.client

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * PSP-style game library screen (landscape).
 * Shows all games available on the connected server device.
 * Tapping a game tells the server to launch it, then opens StreamActivity.
 */
class GameLibraryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_library)

        val deviceLabel = findViewById<TextView>(R.id.deviceLabel)
        deviceLabel.text = "CLOUDX — SESSION ACTIVE"

        val recycler = findViewById<RecyclerView>(R.id.gamesRecycler)
        recycler.layoutManager = GridLayoutManager(this, 4)
        recycler.adapter = GameAdapter(ClientState.games) { game ->
            // Tell the server to launch this game
            ClientState.signalingClient?.launchGame(ClientState.sessionId, game.id)
            // Open the stream screen
            startActivity(Intent(this, StreamActivity::class.java))
        }

        val disconnectBtn = findViewById<TextView>(R.id.disconnectBtn)
        disconnectBtn.setOnClickListener {
            ClientState.signalingClient?.endSession(ClientState.sessionId)
            finishAffinity()
        }
    }

    override fun onBackPressed() {
        // Do nothing — user must use the disconnect button
        // so we don't accidentally kill the session
    }
}

class GameAdapter(
    private val games: List<GameItem>,
    private val onGameClick: (GameItem) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    inner class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.gameNameText)
        val iconText: TextView = view.findViewById(R.id.gameIconText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = games[position]
        holder.nameText.text = game.name
        // Use first letter as icon placeholder until we load real icons
        holder.iconText.text = game.name.first().uppercaseChar().toString()
        holder.itemView.setOnClickListener { onGameClick(game) }
    }

    override fun getItemCount() = games.size
}
