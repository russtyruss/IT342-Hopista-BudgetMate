package budgetmate.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import budgetmate.MainActivity
import budgetmate.R
import java.io.File
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

class ProfileFragment : Fragment() {

    private lateinit var vm: AppViewModel

    private var selectedImageUri: Uri? = null
    private var uploadDialog: AlertDialog? = null
    private var tvSelectedImageName: TextView? = null
    private var btnDialogUploadImage: Button? = null

    private lateinit var ivProfileImage: ImageView
    private lateinit var tvProfileEmail: TextView
    private lateinit var etDisplayName: EditText
    private lateinit var etCurrentPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            ivProfileImage.setImageURI(uri)
            tvSelectedImageName?.text = queryFileName(uri) ?: "Selected image"
            btnDialogUploadImage?.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        vm = (requireActivity() as MainActivity).appViewModel

        ivProfileImage = view.findViewById(R.id.ivProfileImage)
        tvProfileEmail = view.findViewById(R.id.tvProfileEmail)
        etDisplayName = view.findViewById(R.id.etDisplayName)
        etCurrentPassword = view.findViewById(R.id.etCurrentPassword)
        etNewPassword = view.findViewById(R.id.etNewPassword)
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword)

        view.findViewById<Button>(R.id.btnOpenUploadImageDialog).setOnClickListener {
            showUploadDialog(vm)
        }

        view.findViewById<Button>(R.id.btnSaveName).setOnClickListener {
            vm.updateMyName(etDisplayName.text.toString())
        }

        view.findViewById<Button>(R.id.btnChangePassword).setOnClickListener {
            val currentPassword = etCurrentPassword.text.toString()
            val newPassword = etNewPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (newPassword != confirmPassword) {
                Toast.makeText(requireContext(), "New password and confirm password must match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vm.changeMyPassword(currentPassword, newPassword)
            etCurrentPassword.text?.clear()
            etNewPassword.text?.clear()
            etConfirmPassword.text?.clear()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.uiState.collect { state ->
                    val user = state.user
                    if (user != null) {
                        if (etDisplayName.text.toString() != user.name) {
                            etDisplayName.setText(user.name)
                        }
                        tvProfileEmail.text = user.email
                    }

                    val payload = state.profileImagePayload
                    if (payload != null) {
                        val bitmap = decodeSampledBitmapFromBytes(payload.bytes, 1024, 1024)
                        if (bitmap != null) {
                            ivProfileImage.setImageBitmap(bitmap)
                        }
                    } else {
                        ivProfileImage.setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                }
            }
        }

        vm.refreshCurrentUser()
        vm.loadProfileImage()
    }

    override fun onResume() {
        super.onResume()
        if (::vm.isInitialized) {
            vm.refreshCurrentUser()
            vm.loadProfileImage()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uploadDialog?.dismiss()
        uploadDialog = null
        tvSelectedImageName = null
        btnDialogUploadImage = null
    }

    private fun showUploadDialog(vm: AppViewModel) {
        selectedImageUri = null
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_image_upload, null)
        tvSelectedImageName = dialogView.findViewById(R.id.tvSelectedImageName)
        val btnChoose = dialogView.findViewById<Button>(R.id.btnDialogChooseImage)
        btnDialogUploadImage = dialogView.findViewById(R.id.btnDialogUploadImage)

        btnChoose.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        btnDialogUploadImage?.setOnClickListener {
            val uri = selectedImageUri
            if (uri == null) {
                Toast.makeText(requireContext(), "Please choose an image first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val imagePart = createImagePart(uri)
            if (imagePart == null) {
                Toast.makeText(requireContext(), "Unable to read selected image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vm.uploadMyProfileImage(imagePart)
            uploadDialog?.dismiss()
        }

        uploadDialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Upload Profile Picture")
            .setView(dialogView)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun createImagePart(uri: Uri): MultipartBody.Part? {
        return try {
            val resolver = requireContext().contentResolver
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            val fileName = queryFileName(uri) ?: "profile.jpg"

            val tempFile = File(requireContext().cacheDir, "profile-upload-${System.currentTimeMillis()}-$fileName")
            resolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            MultipartBody.Part.createFormData("file", fileName, requestBody)
        } catch (_: Exception) {
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        val resolver = requireContext().contentResolver
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)
            }
        }
        return null
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val sampled = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, sampled)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize.coerceAtLeast(1)
    }
}
