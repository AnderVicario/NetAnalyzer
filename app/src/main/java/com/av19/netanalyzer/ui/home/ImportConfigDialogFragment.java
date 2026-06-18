package com.av19.netanalyzer.ui.home;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.av19.netanalyzer.R;
import com.av19.netanalyzer.data.ScanRecord;
import com.av19.netanalyzer.utils.PreferencesManager;
import com.av19.netanalyzer.utils.SnackbarUtils;

public class ImportConfigDialogFragment extends DialogFragment {

    private static final String ARG_IMPORT_TYPE = "import_type";

    public static final int TYPE_ADVANCED_SETTINGS = 0;
    public static final int TYPE_SCAN_HISTORY = 1;

    private PreferencesManager pm;
    private EditText jsonInput;
    private int importType = TYPE_ADVANCED_SETTINGS;
    private ImportListener listener;

    public interface ImportListener {
        void onImportSuccess();
        default void onImportSuccess(ScanRecord importedRecord) {}
    }

    public void setImportListener(ImportListener listener) {
        this.listener = listener;
    }

    public static ImportConfigDialogFragment newInstance(int importType) {
        ImportConfigDialogFragment fragment = new ImportConfigDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_IMPORT_TYPE, importType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_DeviceDefault_Dialog);
        pm = new PreferencesManager(requireContext());
        if (getArguments() != null) {
            importType = getArguments().getInt(ARG_IMPORT_TYPE, TYPE_ADVANCED_SETTINGS);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(
                    (int) (screenWidth * 0.9),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_config_container, container, false);

        TextView title = root.findViewById(R.id.dialog_title);
        // Cambiar título según el tipo de importación
        if (importType == TYPE_SCAN_HISTORY) {
            title.setText(R.string.import_scan_history_title);
        } else {
            title.setText(R.string.adv_settings_import_title);
        }

        jsonInput = new EditText(requireContext());
        jsonInput.setHint(getString(R.string.adv_settings_import_hint));
        jsonInput.setMinLines(5);
        jsonInput.setMaxLines(10);
        jsonInput.setVerticalScrollBarEnabled(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        jsonInput.setPadding(pad, pad, pad, pad);

        ViewGroup contentContainer = root.findViewById(R.id.content_container);
        contentContainer.addView(jsonInput);

        Button btnSave = root.findViewById(R.id.btn_save);
        Button btnCancel = root.findViewById(R.id.btn_cancel);

        btnCancel.setOnClickListener(v -> dismiss());

        btnSave.setOnClickListener(v -> {
            String json = jsonInput.getText().toString();
            boolean success;
            ScanRecord[] importedRecord = new ScanRecord[1];
            if (importType == TYPE_SCAN_HISTORY) {
                success = pm.importScanHistory(json, importedRecord);
            } else {
                success = pm.importAdvancedSettings(json);
            }

            if (success) {
                // Notify listener or parent fragment
                if (listener != null) {
                    listener.onImportSuccess(importedRecord[0]);
                } else {
                    Fragment parent = getParentFragment();
                    if (parent instanceof ImportListener) {
                        ((ImportListener) parent).onImportSuccess(importedRecord[0]);
                    }
                }
                dismiss();
                // Mostrar el Snackbar después de cerrar el diálogo para que sea visible
                SnackbarUtils.showSuccess(requireActivity().findViewById(android.R.id.content),
                        requireContext(),
                        getString(R.string.adv_settings_import_res_positive));
            } else {
                // Mostrar error sin cerrar el diálogo
                SnackbarUtils.showError(root, requireContext(),
                        getString(R.string.adv_settings_import_res_negative));
            }
        });

        return root;
    }
}