package com.example.filemanagementapp.publiclink

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.filemanagementapp.R

class PublicLinkFragment : Fragment() {
    private var username: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        username = arguments?.getString(ARG_USERNAME).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_public_link, container, false)
        view.findViewById<TextView>(R.id.textPublicLink).text = "Public Links (Coming soon)\nUser: $username"
        return view
    }

    companion object {
        private const val ARG_USERNAME = "username"

        fun newInstance(username: String) = PublicLinkFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_USERNAME, username)
            }
        }
    }
}
