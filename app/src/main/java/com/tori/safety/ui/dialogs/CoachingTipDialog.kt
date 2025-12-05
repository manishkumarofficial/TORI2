package com.tori.safety.ui.dialogs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.tori.safety.R
import com.tori.safety.coaching.CoachingManager
import com.tori.safety.data.model.Language
import com.tori.safety.databinding.DialogCoachingTipBinding

/**
 * Dialog for showing driver coaching tips
 */
class CoachingTipDialog : DialogFragment() {
    
    private lateinit var binding: DialogCoachingTipBinding
    private lateinit var coachingManager: CoachingManager
    private var currentLanguage = Language.ENGLISH
    private var currentTipIndex = 0
    
    interface OnTipActionListener {
        fun onDismissTips()
        fun onNextTip()
    }
    
    private var listener: OnTipActionListener? = null
    
    companion object {
        fun newInstance(
            language: Language = Language.ENGLISH,
            tipIndex: Int = 0
        ): CoachingTipDialog {
            return CoachingTipDialog().apply {
                arguments = Bundle().apply {
                    putString("language", language.name)
                    putInt("tip_index", tipIndex)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_TORI_AlertDialog)
        
        arguments?.let { args ->
            currentLanguage = try {
                Language.valueOf(args.getString("language", "ENGLISH"))
            } catch (e: Exception) {
                Language.ENGLISH
            }
            currentTipIndex = args.getInt("tip_index", 0)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogCoachingTipBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        coachingManager = CoachingManager(requireContext())
        
        setupUI()
        setupClickListeners()
        showTip(currentTipIndex)
    }
    
    private fun setupUI() {
        // Set up the dialog appearance
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
    
    private fun setupClickListeners() {
        binding.btnCloseTip.setOnClickListener {
            dismiss()
        }
        
        binding.btnNextTip.setOnClickListener {
            showNextTip()
        }
        
        binding.btnDismissTips.setOnClickListener {
            listener?.onDismissTips()
            dismiss()
        }
    }
    
    private fun showTip(tipIndex: Int) {
        coachingManager.showSpecificTip(tipIndex, currentLanguage)
        
        // Get the tip text from coaching manager
        val tips = coachingManager.getAllTips(currentLanguage)
        if (tipIndex >= 0 && tipIndex < tips.size) {
            binding.tvTipContent.text = tips[tipIndex]
        }
        
        currentTipIndex = tipIndex
    }
    
    private fun showNextTip() {
        val tips = coachingManager.getAllTips(currentLanguage)
        val nextIndex = (currentTipIndex + 1) % tips.size
        showTip(nextIndex)
        
        listener?.onNextTip()
    }
    
    fun setOnTipActionListener(listener: OnTipActionListener) {
        this.listener = listener
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }
}
