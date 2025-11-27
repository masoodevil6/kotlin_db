package com.gogcompany.mymvvmapplication.presentation.home


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gogcompany.mymvvmapplication.R
import com.gogcompany.mymvvmapplication.data.local.model.Note
import com.gogcompany.mymvvmapplication.databinding.ItemNoteBinding

class HomeAdapter(
    private val onItemClick: (Note) -> Unit ,
    private val onFavoriteClick: (Note , Boolean) -> Unit
) : ListAdapter<Note, HomeAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<Note?>(){
        override fun areItemsTheSame(oldItem: Note, newItem: Note): Boolean{
            return oldItem.id == newItem.id;
        }
        override fun areContentsTheSame(oldItem: Note, newItem: Note): Boolean{
            return oldItem == newItem
        }
    }
) {

    inner class ViewHolder(
        private val binding: ItemNoteBinding
    ) : RecyclerView.ViewHolder(binding.root){

        init {
            binding.root.setOnClickListener {
                onItemClick.invoke(getItem(adapterPosition));
            }
        }

        fun bind(item: Note) {
            with(binding){
                homeAdapterTextNoteTitle.text = item.title;
                homeAdapterTextNoteDescription.text = item.description;
                homeAdapterTextNoteDate.text = StringBuilder().append("Created in:").append( item.createDate);
                homeAdapterImageNoteFavorite.setImageResource(if(item.isFavorite) R.drawable.ic_fav_fill else R.drawable.ic_favorite_empty)

                homeAdapterImageNoteFavorite.setOnClickListener{
                    onFavoriteClick(getItem(adapterPosition) , !item.isFavorite);
                }
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
       return ViewHolder(ItemNoteBinding.inflate(LayoutInflater.from(parent.context) , parent , false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

}