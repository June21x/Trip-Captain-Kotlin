package com.example.tripcaptainkotlin.view.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.tripcaptainkotlin.R
import com.example.tripcaptainkotlin.databinding.ItemPlaceBinding
import com.example.tripcaptainkotlin.model.Place
import com.example.tripcaptainkotlin.view.ui.fragment.RecommendationsFragment

class PlacesAdapter(val fragment: RecommendationsFragment) :
    RecyclerView.Adapter<PlacesAdapter.PlaceViewHolder>() {

    private var placeList: List<Place>? = null

    fun setPlaceList(placeList: List<Place>) {
        if (this.placeList == null) {
            this.placeList = placeList
            notifyItemRangeInserted(0, placeList.size)
        } else {
            this.placeList = placeList
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PlaceViewHolder {
        val binding = DataBindingUtil.inflate(
            LayoutInflater.from(parent.context),
            R.layout.item_place, parent,
            false
        ) as ItemPlaceBinding
        binding.apply {
            recommendationsFragment = fragment

        }
        return PlaceViewHolder(binding)
    }

    override fun getItemCount(): Int = this.placeList?.size ?: 0

    override fun onBindViewHolder(holder: PlaceViewHolder, position: Int) {
        holder.binding.place = placeList?.get(position)
        holder.binding.executePendingBindings()
    }

    open class PlaceViewHolder(val binding: ItemPlaceBinding) :
        RecyclerView.ViewHolder(binding.root)
}