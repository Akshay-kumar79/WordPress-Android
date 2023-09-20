package org.wordpress.android.ui.comments;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.elevation.ElevationOverlayProvider;
import com.google.android.material.snackbar.Snackbar;

import org.apache.commons.text.StringEscapeUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker;
import org.wordpress.android.analytics.AnalyticsTracker.Stat;
import org.wordpress.android.databinding.CommentActionFooterBinding;
import org.wordpress.android.databinding.CommentDetailFragmentBinding;
import org.wordpress.android.databinding.ReaderIncludeCommentBoxBinding;
import org.wordpress.android.datasets.NotificationsTable;
import org.wordpress.android.datasets.ReaderPostTable;
import org.wordpress.android.datasets.UserSuggestionTable;
import org.wordpress.android.fluxc.action.CommentAction;
import org.wordpress.android.fluxc.generated.CommentActionBuilder;
import org.wordpress.android.fluxc.model.CommentModel;
import org.wordpress.android.fluxc.model.CommentStatus;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.store.AccountStore;
import org.wordpress.android.fluxc.store.CommentStore.OnCommentChanged;
import org.wordpress.android.fluxc.store.CommentStore.RemoteCommentPayload;
import org.wordpress.android.fluxc.store.CommentStore.RemoteCreateCommentPayload;
import org.wordpress.android.fluxc.store.CommentStore.RemoteLikeCommentPayload;
import org.wordpress.android.fluxc.store.CommentsStore;
import org.wordpress.android.fluxc.store.SiteStore;
import org.wordpress.android.fluxc.tools.FluxCImageLoader;
import org.wordpress.android.models.Note;
import org.wordpress.android.models.Note.EnabledActions;
import org.wordpress.android.models.UserSuggestion;
import org.wordpress.android.models.usecases.LocalCommentCacheUpdateHandler;
import org.wordpress.android.ui.ActivityId;
import org.wordpress.android.ui.CollapseFullScreenDialogFragment;
import org.wordpress.android.ui.CollapseFullScreenDialogFragment.Builder;
import org.wordpress.android.ui.CollapseFullScreenDialogFragment.OnCollapseListener;
import org.wordpress.android.ui.CollapseFullScreenDialogFragment.OnConfirmListener;
import org.wordpress.android.ui.CommentFullScreenDialogFragment;
import org.wordpress.android.ui.ViewPagerFragment;
import org.wordpress.android.ui.comments.CommentActions.OnCommentActionListener;
import org.wordpress.android.ui.comments.CommentActions.OnNoteCommentActionListener;
import org.wordpress.android.ui.comments.unified.CommentIdentifier;
import org.wordpress.android.ui.comments.unified.CommentIdentifier.NotificationCommentIdentifier;
import org.wordpress.android.ui.comments.unified.CommentIdentifier.SiteCommentIdentifier;
import org.wordpress.android.ui.comments.unified.CommentSource;
import org.wordpress.android.ui.comments.unified.CommentsStoreAdapter;
import org.wordpress.android.ui.comments.unified.UnifiedCommentsEditActivity;
import org.wordpress.android.ui.notifications.NotificationEvents;
import org.wordpress.android.ui.notifications.NotificationFragment;
import org.wordpress.android.ui.notifications.NotificationsDetailListFragment;
import org.wordpress.android.ui.reader.ReaderActivityLauncher;
import org.wordpress.android.ui.reader.ReaderAnim;
import org.wordpress.android.ui.reader.actions.ReaderActions;
import org.wordpress.android.ui.reader.actions.ReaderPostActions;
import org.wordpress.android.ui.suggestion.Suggestion;
import org.wordpress.android.ui.suggestion.adapters.SuggestionAdapter;
import org.wordpress.android.ui.suggestion.service.SuggestionEvents;
import org.wordpress.android.ui.suggestion.util.SuggestionServiceConnectionManager;
import org.wordpress.android.ui.suggestion.util.SuggestionUtils;
import org.wordpress.android.util.AniUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.ColorUtils;
import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.EditTextUtils;
import org.wordpress.android.util.GravatarUtils;
import org.wordpress.android.util.HtmlUtils;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.SiteUtils;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.util.WPLinkMovementMethod;
import org.wordpress.android.util.analytics.AnalyticsUtils;
import org.wordpress.android.util.config.UnifiedCommentsCommentEditFeatureConfig;
import org.wordpress.android.util.extensions.ContextExtensionsKt;
import org.wordpress.android.util.extensions.ViewExtensionsKt;
import org.wordpress.android.util.image.ImageManager;
import org.wordpress.android.util.image.ImageType;
import org.wordpress.android.widgets.WPSnackbar;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.DelicateCoroutinesApi;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;

/**
 * comment detail displayed from both the notification list and the comment list
 * prior to this there were separate comment detail screens for each list
 *
 * @deprecated Comments are being refactored as part of Comments Unification project. If you are adding any
 * features or modifying this class, please ping develric or klymyam
 */
@Deprecated
public class CommentDetailFragment extends ViewPagerFragment implements NotificationFragment, OnConfirmListener,
        OnCollapseListener {
    private static final String KEY_MODE = "KEY_MODE";
    private static final String KEY_SITE_LOCAL_ID = "KEY_SITE_LOCAL_ID";
    private static final String KEY_COMMENT_ID = "KEY_COMMENT_ID";
    private static final String KEY_NOTE_ID = "KEY_NOTE_ID";
    private static final String KEY_REPLY_TEXT = "KEY_REPLY_TEXT";

    private static final int INTENT_COMMENT_EDITOR = 1010;

    private CommentModel mComment;
    private SiteModel mSite;

    @Nullable private Note mNote;
    private SuggestionAdapter mSuggestionAdapter;
    private SuggestionServiceConnectionManager mSuggestionServiceConnectionManager;
    private String mRestoredReplyText;
    private String mRestoredNoteId;
    private boolean mIsUsersBlog = false;
    private boolean mShouldFocusReplyField;
    private String mPreviousStatus;
    private float mNormalOpacity = 1f;
    private float mMediumOpacity;

    @Inject AccountStore mAccountStore;
    @Inject CommentsStoreAdapter mCommentsStoreAdapter;
    @Inject SiteStore mSiteStore;
    @Inject FluxCImageLoader mImageLoader;
    @Inject ImageManager mImageManager;
    @Inject CommentsStore mCommentsStore;
    @Inject LocalCommentCacheUpdateHandler mLocalCommentCacheUpdateHandler;
    @Inject UnifiedCommentsCommentEditFeatureConfig mUnifiedCommentsCommentEditFeatureConfig;

    private boolean mIsSubmittingReply = false;
    private NotificationsDetailListFragment mNotificationsDetailListFragment;
    private OnPostClickListener mOnPostClickListener;
    private OnCommentActionListener mOnCommentActionListener;
    private OnNoteCommentActionListener mOnNoteCommentActionListener;

    private CommentSource mCommentSource;

    /*
     * these determine which actions (moderation, replying, marking as spam) to enable
     * for this comment - all actions are enabled when opened from the comment list, only
     * changed when opened from a notification
     */
    private EnumSet<EnabledActions> mEnabledActions = EnumSet.allOf(EnabledActions.class);

    @Nullable private CommentDetailFragmentBinding mBinding = null;
    @Nullable private ReaderIncludeCommentBoxBinding mReplyBinding = null;
    @Nullable private CommentActionFooterBinding mActionBinding = null;

    /*
     * used when called from comment list
     */
    static CommentDetailFragment newInstance(
            @NonNull SiteModel site,
            CommentModel commentModel
    ) {
        CommentDetailFragment fragment = new CommentDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(KEY_MODE, CommentSource.SITE_COMMENTS);
        args.putInt(KEY_SITE_LOCAL_ID, site.getId());
        args.putLong(KEY_COMMENT_ID, commentModel.getRemoteCommentId());
        fragment.setArguments(args);
        return fragment;
    }

    /*
     * used when called from notification list for a comment notification
     */
    public static CommentDetailFragment newInstance(final String noteId, final String replyText) {
        CommentDetailFragment fragment = new CommentDetailFragment();
        Bundle args = new Bundle();
        args.putSerializable(KEY_MODE, CommentSource.NOTIFICATION);
        args.putString(KEY_NOTE_ID, noteId);
        args.putString(KEY_REPLY_TEXT, replyText);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getActivity().getApplication()).component().inject(this);

        mCommentSource = (CommentSource) getArguments().getSerializable(KEY_MODE);

        switch (mCommentSource) {
            case SITE_COMMENTS:
                setComment(getArguments().getLong(KEY_COMMENT_ID), getArguments().getInt(KEY_SITE_LOCAL_ID));
                break;
            case NOTIFICATION:
                setNote(getArguments().getString(KEY_NOTE_ID));
                setReplyText(getArguments().getString(KEY_REPLY_TEXT));
                break;
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.getString(KEY_NOTE_ID) != null) {
                // The note will be set in onResume()
                // See WordPress.deferredInit()
                mRestoredNoteId = savedInstanceState.getString(KEY_NOTE_ID);
            } else {
                int siteId = savedInstanceState.getInt(KEY_SITE_LOCAL_ID);
                long commentId = savedInstanceState.getLong(KEY_COMMENT_ID);
                setComment(commentId, siteId);
            }
        }

        setHasOptionsMenu(true);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mComment != null) {
            outState.putLong(KEY_COMMENT_ID, mComment.getRemoteCommentId());
            outState.putInt(KEY_SITE_LOCAL_ID, mSite.getId());
        }

        if (mNote != null) {
            outState.putString(KEY_NOTE_ID, mNote.getId());
        }
    }

    @Override
    public void onDestroy() {
        if (mSuggestionServiceConnectionManager != null) {
            mSuggestionServiceConnectionManager.unbindFromService();
        }
        super.onDestroy();
    }

    // touching the file resulted in the MethodLength, it's suppressed until we get time to refactor this method
    @SuppressWarnings("checkstyle:MethodLength")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = CommentDetailFragmentBinding.inflate(inflater, container, false);
        mReplyBinding = mBinding.layoutCommentBox;
        mActionBinding = CommentActionFooterBinding.inflate(inflater, null, false);

        mMediumOpacity = ResourcesCompat.getFloat(
                getResources(),
                com.google.android.material.R.dimen.material_emphasis_medium
        );

        ElevationOverlayProvider elevationOverlayProvider = new ElevationOverlayProvider(
                mBinding.getRoot().getContext()
        );
        float appbarElevation = getResources().getDimension(R.dimen.appbar_elevation);
        int elevatedColor = elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(appbarElevation);

        mReplyBinding.layoutContainer.setBackgroundColor(elevatedColor);

        mReplyBinding.btnSubmitReply.setEnabled(false);
        mReplyBinding.btnSubmitReply.setOnLongClickListener(view1 -> {
            if (view1.isHapticFeedbackEnabled()) {
                view1.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }

            Toast.makeText(view1.getContext(), R.string.send, Toast.LENGTH_SHORT).show();
            return true;
        });
        ViewExtensionsKt.redirectContextClickToLongPressListener(mReplyBinding.btnSubmitReply);

        mReplyBinding.editComment.initializeWithPrefix('@');
        mReplyBinding.editComment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mReplyBinding.btnSubmitReply.setEnabled(!TextUtils.isEmpty(s.toString().trim()));
            }
        });

        mReplyBinding.buttonExpand.setOnClickListener(
                v -> {
                    Bundle bundle = CommentFullScreenDialogFragment.Companion.newBundle(
                            mReplyBinding.editComment.getText().toString(),
                            mReplyBinding.editComment.getSelectionStart(),
                            mReplyBinding.editComment.getSelectionEnd(),
                            mSite.getSiteId()
                    );

                    new Builder(requireContext())
                            .setTitle(R.string.comment)
                            .setOnCollapseListener(this)
                            .setOnConfirmListener(this)
                            .setContent(CommentFullScreenDialogFragment.class, bundle)
                            .setAction(R.string.send)
                            .setHideActivityBar(true)
                            .build()
                            .show(requireActivity().getSupportFragmentManager(),
                                    CollapseFullScreenDialogFragment.TAG + getCommentSpecificFragmentTagSuffix());
                }
        );
        mReplyBinding.buttonExpand.setOnLongClickListener(v -> {
            if (v.isHapticFeedbackEnabled()) {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }

            Toast.makeText(v.getContext(), R.string.description_expand, Toast.LENGTH_SHORT).show();
            return true;
        });
        ViewExtensionsKt.redirectContextClickToLongPressListener(mReplyBinding.buttonExpand);
        setReplyUniqueId();

        // hide comment like button until we know it can be enabled in showCommentAsNotification()
        mActionBinding.btnLike.setVisibility(View.GONE);

        // hide moderation buttons until updateModerationButtons() is called
        mActionBinding.layoutButtons.setVisibility(View.GONE);

        // this is necessary in order for anchor tags in the comment text to be clickable
        mBinding.textContent.setLinksClickable(true);
        mBinding.textContent.setMovementMethod(WPLinkMovementMethod.getInstance());

        mReplyBinding.editComment.setHint(R.string.reader_hint_comment_on_comment);
        mReplyBinding.editComment.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEND) {
                submitReply();
            }
            return false;
        });

        if (!TextUtils.isEmpty(mRestoredReplyText)) {
            mReplyBinding.editComment.setText(mRestoredReplyText);
            mRestoredReplyText = null;
        }

        mReplyBinding.btnSubmitReply.setOnClickListener(v -> submitReply());

        mActionBinding.btnSpam.setOnClickListener(v -> {
            if (mComment == null) {
                return;
            }

            if (CommentStatus.fromString(mComment.getStatus()) == CommentStatus.SPAM) {
                moderateComment(CommentStatus.APPROVED);
                announceCommentStatusChangeForAccessibility(CommentStatus.UNSPAM);
            } else {
                moderateComment(CommentStatus.SPAM);
                announceCommentStatusChangeForAccessibility(CommentStatus.SPAM);
            }
        });

        mActionBinding.btnLike.setOnClickListener(v -> likeComment(false));

        mActionBinding.btnMore.setOnClickListener(v -> showMoreMenu(v));
        // hide more button until we know it can be enabled
        mActionBinding.btnMore.setVisibility(View.GONE);

        setupSuggestionServiceAndAdapter();

        return mBinding.getRoot();
    }

    private String getCommentSpecificFragmentTagSuffix() {
        return "_" + mComment.getRemoteSiteId() + "_" + mComment.getRemoteCommentId();
    }

    @Override
    public void onConfirm(@Nullable Bundle result) {
        if (mReplyBinding != null && result != null) {
            mReplyBinding.editComment.setText(result.getString(CommentFullScreenDialogFragment.RESULT_REPLY));
            submitReply();
        }
    }

    @Override
    public void onCollapse(@Nullable Bundle result) {
        if (mReplyBinding != null && result != null) {
            mReplyBinding.editComment.setText(result.getString(CommentFullScreenDialogFragment.RESULT_REPLY));
            mReplyBinding.editComment.setSelection(result.getInt(
                            CommentFullScreenDialogFragment.RESULT_SELECTION_START),
                    result.getInt(CommentFullScreenDialogFragment.RESULT_SELECTION_END));
            mReplyBinding.editComment.requestFocus();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ActivityId.trackLastActivity(ActivityId.COMMENT_DETAIL);

        // Set the note if we retrieved the noteId from savedInstanceState
        if (!TextUtils.isEmpty(mRestoredNoteId)) {
            setNote(mRestoredNoteId);
            mRestoredNoteId = null;
        }

        // reattach listeners to collapsible reply dialog
        // we need to to it in onResume to make sure mComment is already intialized
        CollapseFullScreenDialogFragment fragment =
                (CollapseFullScreenDialogFragment) requireActivity().getSupportFragmentManager().findFragmentByTag(
                        CollapseFullScreenDialogFragment.TAG + getCommentSpecificFragmentTagSuffix());

        if (fragment != null && fragment.isAdded()) {
            fragment.setOnCollapseListener(this);
            fragment.setOnConfirmListener(this);
        }
    }

    private void setupSuggestionServiceAndAdapter() {
        if (!isAdded() || mSite == null || !SiteUtils.isAccessedViaWPComRest(mSite)) {
            return;
        }
        mSuggestionServiceConnectionManager = new SuggestionServiceConnectionManager(getActivity(), mSite.getSiteId());
        mSuggestionAdapter = SuggestionUtils.setupUserSuggestions(mSite, getActivity(),
                mSuggestionServiceConnectionManager);
        if (mReplyBinding != null && mSuggestionAdapter != null) {
            mReplyBinding.editComment.setAdapter(mSuggestionAdapter);
        }
    }

    private void setReplyUniqueId() {
        if (isAdded()) {
            String sId = null;
            if (mSite != null && mComment != null) {
                sId = String.format(Locale.US, "%d-%d", mSite.getSiteId(), mComment.getRemoteCommentId());
            } else if (mNote != null) {
                sId = String.format(Locale.US, "%d-%d", mNote.getSiteId(), mNote.getCommentId());
            }
            if (mReplyBinding != null && sId != null) {
                mReplyBinding.editComment.getAutoSaveTextHelper().setUniqueId(sId);
                mReplyBinding.editComment.getAutoSaveTextHelper().loadString(mReplyBinding.editComment);
            }
        }
    }

    private void setComment(final long commentRemoteId, final int siteLocalId) {
        final SiteModel site = mSiteStore.getSiteByLocalId(siteLocalId);
        setComment(mCommentsStoreAdapter.getCommentBySiteAndRemoteId(site, commentRemoteId), site);
    }

    private void setComment(@Nullable final CommentModel comment, @Nullable final SiteModel site) {
        mComment = comment;
        mSite = site;

        // is this comment on one of the user's blogs? it won't be if this was displayed from a
        // notification about a reply to a comment this user posted on someone else's blog
        mIsUsersBlog = (comment != null && site != null);

        if (isAdded()) {
            showComment();
        }

        // Reset the reply unique id since mComment just changed.
        setReplyUniqueId();
    }

    private void disableShouldFocusReplyField() {
        mShouldFocusReplyField = false;
    }

    public void enableShouldFocusReplyField() {
        mShouldFocusReplyField = true;
    }

    @Override
    public Note getNote() {
        return mNote;
    }

    private SiteModel createDummyWordPressComSite(long siteId) {
        SiteModel site = new SiteModel();
        site.setIsWPCom(true);
        site.setOrigin(SiteModel.ORIGIN_WPCOM_REST);
        site.setSiteId(siteId);
        return site;
    }

    public void setNote(Note note) {
        mNote = note;
        mSite = mSiteStore.getSiteBySiteId(note.getSiteId());
        if (mSite == null) {
            // This should not exist, we should clean that screen so a note without a site/comment can be displayed
            mSite = createDummyWordPressComSite(mNote.getSiteId());
        }
        if (isAdded() && mNote != null) {
            showComment();
        }
    }

    @Override
    public void setNote(String noteId) {
        if (noteId == null) {
            showErrorToastAndFinish();
            return;
        }

        Note note = NotificationsTable.getNoteById(noteId);
        if (note == null) {
            showErrorToastAndFinish();
            return;
        }
        setNote(note);
    }

    private void setReplyText(String replyText) {
        if (replyText == null) {
            return;
        }
        mRestoredReplyText = replyText;
    }

    private void showErrorToastAndFinish() {
        AppLog.e(AppLog.T.NOTIFS, "Note could not be found.");
        if (getActivity() != null) {
            ToastUtils.showToast(getActivity(), R.string.error_notification_open);
            getActivity().finish();
        }
    }

    @SuppressWarnings("deprecation") // TODO: Remove when minSdkVersion >= 23
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        if (activity instanceof OnPostClickListener) {
            mOnPostClickListener = (OnPostClickListener) activity;
        }
        if (activity instanceof OnCommentActionListener) {
            mOnCommentActionListener = (OnCommentActionListener) activity;
        }
        if (activity instanceof OnNoteCommentActionListener) {
            mOnNoteCommentActionListener = (OnNoteCommentActionListener) activity;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        mCommentsStoreAdapter.register(this);
        showComment();
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        mCommentsStoreAdapter.unregister(this);
        super.onStop();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(SuggestionEvents.SuggestionNameListUpdated event) {
        // check if the updated suggestions are for the current blog and update the suggestions
        if (event.mRemoteBlogId != 0
            && mSite != null
            && event.mRemoteBlogId == mSite.getSiteId()
            && mSuggestionAdapter != null
        ) {
            List<UserSuggestion> userSuggestions = UserSuggestionTable.getSuggestionsForSite(event.mRemoteBlogId);
            List<Suggestion> suggestions = Suggestion.Companion.fromUserSuggestions(userSuggestions);
            mSuggestionAdapter.setSuggestionList(suggestions);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == INTENT_COMMENT_EDITOR && resultCode == Activity.RESULT_OK) {
            reloadComment();
        }
    }

    /**
     * Reload the current comment from the local database
     */
    private void reloadComment() {
        if (mComment == null) {
            return;
        }
        CommentModel updatedComment = mCommentsStoreAdapter.getCommentByLocalId(mComment.getId());
        if (updatedComment != null) {
            setComment(updatedComment, mSite);
        }
        if (mNotificationsDetailListFragment != null) {
            mNotificationsDetailListFragment.refreshBlocksForEditedComment(mNote.getId());
        }
    }

    /**
     * open the comment for editing
     */
    private void editComment() {
        if (!isAdded() || mComment == null) {
            return;
        }
        AnalyticsUtils.trackCommentActionWithSiteDetails(Stat.COMMENT_EDITOR_OPENED,
                mCommentSource.toAnalyticsCommentActionSource(), mSite);

        // IMPORTANT: don't use getActivity().startActivityForResult() or else onActivityResult()
        // won't be called in this fragment
        // https://code.google.com/p/android/issues/detail?id=15394#c45
        if (mUnifiedCommentsCommentEditFeatureConfig.isEnabled()) {
            final CommentIdentifier commentIdentifier = mapCommentIdentifier();
            final Intent intent =
                    UnifiedCommentsEditActivity.createIntent(requireActivity(), commentIdentifier, mSite);
            startActivityForResult(intent, INTENT_COMMENT_EDITOR);
        } else {
            Intent intent = new Intent(getActivity(), EditCommentActivity.class);
            intent.putExtra(WordPress.SITE, mSite);
            intent.putExtra(EditCommentActivity.KEY_COMMENT, mComment);
            if (mNote != null && mComment == null) {
                intent.putExtra(EditCommentActivity.KEY_NOTE_ID, mNote.getId());
            }
            startActivityForResult(intent, INTENT_COMMENT_EDITOR);
        }
    }

    @Nullable
    private CommentIdentifier mapCommentIdentifier() {
        switch (mCommentSource) {
            case SITE_COMMENTS:
                return new SiteCommentIdentifier(mComment.getId(), mComment.getRemoteCommentId());
            case NOTIFICATION:
                return new NotificationCommentIdentifier(mNote.getId(), mNote.getCommentId());
            default:
                return null;
        }
    }

    /*
     * display the current comment
     */
    private void showComment() {
        if (mBinding == null || !isAdded() || getView() == null) {
            return;
        }

        // these two views contain all the other views except the progress bar
        // hide container views when comment is null (will happen when opened from a notification)
        if (mComment == null) {
            mBinding.nestedScrollView.setVisibility(View.GONE);
            mBinding.layoutBottom.setVisibility(View.GONE);

            if (mNote != null) {
                SiteModel site = mSiteStore.getSiteBySiteId(mNote.getSiteId());
                if (site == null) {
                    // This should not exist, we should clean that screen so a note without a site/comment
                    // can be displayed
                    site = createDummyWordPressComSite(mNote.getSiteId());
                }

                // Check if the comment is already in our store
                CommentModel comment = mCommentsStoreAdapter.getCommentBySiteAndRemoteId(site, mNote.getCommentId());
                if (comment != null) {
                    // It exists, then show it as a "Notification"
                    showCommentAsNotification(mNote, site, comment);
                } else {
                    // It's not in our store yet, request it.
                    RemoteCommentPayload payload = new RemoteCommentPayload(site, mNote.getCommentId());
                    mCommentsStoreAdapter.dispatch(CommentActionBuilder.newFetchCommentAction(payload));
                    setProgressVisible(true);

                    // Show a "temporary" comment built from the note data, the view will be refreshed once the
                    // comment has been fetched.
                    showCommentAsNotification(mNote, site, null);
                }
            }
            return;
        }

        mBinding.nestedScrollView.setVisibility(View.VISIBLE);
        mBinding.layoutBottom.setVisibility(View.VISIBLE);

        // Add action buttons footer
        if (mNote == null && mActionBinding != null && mActionBinding.layoutButtons.getParent() == null) {
            mBinding.commentContentContainer.addView(mActionBinding.layoutButtons);
        }

        mBinding.textName.setText(
                mComment.getAuthorName() == null ? getString(R.string.anonymous) : mComment.getAuthorName()
        );
        mBinding.textDate.setText(
                DateTimeUtils.javaDateToTimeSpan(
                        DateTimeUtils.dateFromIso8601(mComment.getDatePublished()), WordPress.getContext()
                )
        );

        String renderingError = getString(R.string.comment_unable_to_show_error);
        mBinding.textContent.post(() -> CommentUtils.displayHtmlComment(
                mBinding.textContent,
                mComment.getContent(),
                mBinding.textContent.getWidth(),
                mBinding.textContent.getLineHeight(),
                renderingError
        ));

        int avatarSz = getResources().getDimensionPixelSize(R.dimen.avatar_sz_large);
        String avatarUrl = "";
        if (mComment.getAuthorProfileImageUrl() != null) {
            avatarUrl = GravatarUtils.fixGravatarUrl(mComment.getAuthorProfileImageUrl(), avatarSz);
        } else if (mComment.getAuthorEmail() != null) {
            avatarUrl = GravatarUtils.gravatarFromEmail(mComment.getAuthorEmail(), avatarSz);
        }
        mImageManager.loadIntoCircle(mBinding.imageAvatar, ImageType.AVATAR_WITH_BACKGROUND, avatarUrl);

        updateStatusViews();

        // navigate to author's blog when avatar or name clicked
        if (mComment.getAuthorUrl() != null) {
            View.OnClickListener authorListener =
                    v -> ReaderActivityLauncher.openUrl(getActivity(), mComment.getAuthorUrl());
            mBinding.imageAvatar.setOnClickListener(authorListener);
            mBinding.textName.setOnClickListener(authorListener);
            mBinding.textName.setTextColor(ContextExtensionsKt.getColorFromAttribute(
                    mBinding.textName.getContext(),
                    com.google.android.material.R.attr.colorPrimary)
            );
        } else {
            mBinding.textName.setTextColor(ContextExtensionsKt.getColorFromAttribute(
                    mBinding.textName.getContext(),
                    com.google.android.material.R.attr.colorOnSurface)
            );
        }

        showPostTitle(mSite, mComment.getRemotePostId());

        // make sure reply box is showing
        if (mReplyBinding != null && mReplyBinding.layoutContainer.getVisibility() != View.VISIBLE && canReply()) {
            AniUtils.animateBottomBar(mReplyBinding.layoutContainer, true);
            if (mShouldFocusReplyField) {
                mReplyBinding.editComment.performClick();
                disableShouldFocusReplyField();
            }
        }

        getActivity().invalidateOptionsMenu();
    }

    /*
     * displays the passed post title for the current comment, updates stored title if one doesn't exist
     */
    private void setPostTitle(TextView txtTitle, String postTitle, boolean isHyperlink) {
        if (txtTitle == null || !isAdded()) {
            return;
        }
        if (TextUtils.isEmpty(postTitle)) {
            txtTitle.setText(R.string.untitled);
            return;
        }

        // if comment doesn't have a post title, set it to the passed one and save to comment table
        if (mComment != null && mComment.getPostTitle() == null) {
            mComment.setPostTitle(postTitle);
            mCommentsStoreAdapter.dispatch(CommentActionBuilder.newUpdateCommentAction(mComment));
        }

        // display "on [Post Title]..."
        if (isHyperlink) {
            String html = getString(R.string.on)
                          + " <font color=" + HtmlUtils.colorResToHtmlColor(getActivity(),
                    ContextExtensionsKt.getColorResIdFromAttribute(
                            getActivity(),
                            com.google.android.material.R.attr.colorPrimary
                    ))
                          + ">"
                          + postTitle.trim()
                          + "</font>";
            txtTitle.setText(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY));
        } else {
            String text = getString(R.string.on) + " " + postTitle.trim();
            txtTitle.setText(text);
        }
    }

    /*
     * ensure the post associated with this comment is available to the reader and show its
     * title above the comment
     */
    private void showPostTitle(final SiteModel site, final long postId) {
        if (mBinding == null || !isAdded()) {
            return;
        }

        boolean postExists = ReaderPostTable.postExists(site.getSiteId(), postId);

        // the post this comment is on can only be requested if this is a .com blog or a
        // jetpack-enabled self-hosted blog, and we have valid .com credentials
        boolean canRequestPost = SiteUtils.isAccessedViaWPComRest(site) && mAccountStore.hasAccessToken();

        final String title;
        final boolean hasTitle;
        if (mComment.getPostTitle() != null) {
            // use comment's stored post title if available
            title = mComment.getPostTitle();
            hasTitle = true;
        } else if (postExists) {
            // use title from post if available
            title = ReaderPostTable.getPostTitle(site.getSiteId(), postId);
            hasTitle = !TextUtils.isEmpty(title);
        } else {
            title = null;
            hasTitle = false;
        }
        if (hasTitle) {
            setPostTitle(mBinding.textPostTitle, title, canRequestPost);
        } else if (canRequestPost) {
            mBinding.textPostTitle.setText(postExists ? R.string.untitled : R.string.loading);
        }

        // if this is a .com or jetpack blog, tapping the title shows the associated post
        // in the reader
        if (canRequestPost) {
            // first make sure this post is available to the reader, and once it's retrieved set
            // the title if it wasn't set above
            if (!postExists) {
                AppLog.d(T.COMMENTS, "comment detail > retrieving post");
                ReaderPostActions
                        .requestBlogPost(site.getSiteId(), postId, new ReaderActions.OnRequestListener<String>() {
                            @Override
                            public void onSuccess(String blogUrl) {
                                if (!isAdded()) {
                                    return;
                                }

                                // update title if it wasn't set above
                                if (!hasTitle) {
                                    String postTitle = ReaderPostTable.getPostTitle(site.getSiteId(), postId);
                                    if (!TextUtils.isEmpty(postTitle)) {
                                        setPostTitle(mBinding.textPostTitle, postTitle, true);
                                    } else {
                                        mBinding.textPostTitle.setText(R.string.untitled);
                                    }
                                }
                            }

                            @Override
                            public void onFailure(int statusCode) {
                            }
                        });
            }

            mBinding.textPostTitle.setOnClickListener(v -> {
                if (mOnPostClickListener != null) {
                    mOnPostClickListener.onPostClicked(getNote(), site.getSiteId(),
                            (int) mComment.getRemotePostId());
                } else {
                    // right now this will happen from notifications
                    AppLog.i(T.COMMENTS, "comment detail > no post click listener");
                    ReaderActivityLauncher.showReaderPostDetail(getActivity(), site.getSiteId(),
                            mComment.getRemotePostId());
                }
            });
        }
    }

    // TODO klymyam remove legacy comment tracking after new comments are shipped and new funnels are made
    private void trackModerationEvent(final CommentStatus newStatus) {
        switch (newStatus) {
            case APPROVED:
                if (mCommentSource == CommentSource.NOTIFICATION) {
                    AnalyticsTracker.track(Stat.NOTIFICATION_APPROVED);
                }
                AnalyticsUtils.trackCommentActionWithSiteDetails(Stat.COMMENT_APPROVED,
                        mCommentSource.toAnalyticsCommentActionSource(), mSite);
                break;
            case UNAPPROVED:
                if (mCommentSource == CommentSource.NOTIFICATION) {
                    AnalyticsTracker.track(Stat.NOTIFICATION_UNAPPROVED);
                }
                AnalyticsUtils.trackCommentActionWithSiteDetails(Stat.COMMENT_UNAPPROVED,
                        mCommentSource.toAnalyticsCommentActionSource(), mSite);
                break;
            case SPAM:
                if (mCommentSource == CommentSource.NOTIFICATION) {
                    AnalyticsTracker.track(Stat.NOTIFICATION_FLAGGED_AS_SPAM);
                }
                AnalyticsUtils.trackCommentActionWithSiteDetails(Stat.COMMENT_SPAMMED,
                        mCommentSource.toAnalyticsCommentActionSource(), mSite);
                break;
            case UNSPAM:
                AnalyticsUtils.trackCommentActionWithSiteDetails(Stat.COMMENT_UNSPAMMED,
                        mCommentSource.toAnalyticsCommentActionSource(), mSite);
                break;
            case TRASH:
                if (mCommentSource == CommentSource.NOTIFICATION) {
                    AnalyticsTracker.track(Stat.NOTIFICATION_TRASHED);
                }
                AnalyticsUtils.trackCommentActionWithSiteDetails(Stat.COMMENT_TRASHED,
                        mCommentSource.toAnalyticsCommentActionSource(), mSite);
                break;
            case UNTRASH:
                AnalyticsUtils.trackCommentActionWithSiteDetails(Stat.COMMENT_UNTRASHED,
                        mCommentSource.toAnalyticsCommentActionSource(), mSite);
                break;
            case DELETED:
                AnalyticsUtils.trackCommentActionWithSiteDetails(Stat.COMMENT_DELETED,
                        mCommentSource.toAnalyticsCommentActionSource(), mSite);
                break;
            case ALL:
                break;
        }
    }

    /*
     * approve, disapprove, spam, or trash the current comment
     */
    private void moderateComment(@NonNull CommentStatus newStatus) {
        if (!isAdded() || mComment == null) {
            return;
        }
        if (!NetworkUtils.checkConnection(getActivity())) {
            return;
        }

        mPreviousStatus = mComment.getStatus();

        // Restoring comment from trash or spam works by approving it, but we want to track the actual action
        // instead of generic Approve action
        CommentStatus statusToTrack;
        if (CommentStatus.fromString(mPreviousStatus) == CommentStatus.SPAM && newStatus == CommentStatus.APPROVED) {
            statusToTrack = CommentStatus.UNSPAM;
        } else if (CommentStatus.fromString(mPreviousStatus) == CommentStatus.TRASH
                   && newStatus == CommentStatus.APPROVED) {
            statusToTrack = CommentStatus.UNTRASH;
        } else {
            statusToTrack = newStatus;
        }

        trackModerationEvent(statusToTrack);

        // Fire the appropriate listener if we have one
        if (mNote != null && mOnNoteCommentActionListener != null) {
            mOnNoteCommentActionListener.onModerateCommentForNote(mNote, newStatus);
            dispatchModerationAction(newStatus);
        } else if (mOnCommentActionListener != null) {
            mOnCommentActionListener.onModerateComment(mSite, mComment, newStatus);
            // Sad, but onModerateComment does the moderation itself (due to the undo bar), this should be refactored,
            // That's why we don't call dispatchModerationAction() here.
        }

        updateStatusViews();
    }

    private void dispatchModerationAction(CommentStatus newStatus) {
        if (newStatus == CommentStatus.DELETED) {
            // For deletion, we need to dispatch a specific action.
            mCommentsStoreAdapter
                    .dispatch(CommentActionBuilder.newDeleteCommentAction(new RemoteCommentPayload(mSite, mComment)));
        } else {
            // Actual moderation (push the modified comment).
            mComment.setStatus(newStatus.toString());
            mCommentsStoreAdapter
                    .dispatch(CommentActionBuilder.newPushCommentAction(new RemoteCommentPayload(mSite, mComment)));
        }
    }

    /*
     * post comment box text as a reply to the current comment
     */
    private void submitReply() {
        if (mReplyBinding == null || mComment == null || !isAdded() || mIsSubmittingReply) {
            return;
        }

        if (!NetworkUtils.checkConnection(getActivity())) {
            return;
        }

        final String replyText = EditTextUtils.getText(mReplyBinding.editComment);
        if (TextUtils.isEmpty(replyText)) {
            return;
        }

        // disable editor, hide soft keyboard, hide submit icon, and show progress spinner while submitting
        mReplyBinding.editComment.setEnabled(false);
        EditTextUtils.hideSoftInput(mReplyBinding.editComment);
        mReplyBinding.btnSubmitReply.setVisibility(View.GONE);
        mReplyBinding.progressSubmitComment.setVisibility(View.VISIBLE);

        mIsSubmittingReply = true;

        AnalyticsUtils.trackCommentReplyWithDetails(
                false, mSite, mComment, mCommentSource.toAnalyticsCommentActionSource());

        // Pseudo comment reply
        CommentModel reply = new CommentModel();
        reply.setContent(replyText);

        mCommentsStoreAdapter
                .dispatch(CommentActionBuilder.newCreateNewCommentAction(new RemoteCreateCommentPayload(mSite,
                        mComment,
                        reply)));
    }

    /*
     * update the text, drawable & click listener for mBtnModerate based on
     * the current status of the comment, show mBtnSpam if the comment isn't
     * already marked as spam, and show the current status of the comment
     */
    private void updateStatusViews() {
        if (!isAdded() || mComment == null) {
            return;
        }

        final int statusTextResId; // string resource id for status text
        final int statusColor; // color for status text

        CommentStatus commentStatus = CommentStatus.fromString(mComment.getStatus());
        switch (commentStatus) {
            case APPROVED:
                statusTextResId = R.string.comment_status_approved;
                statusColor = ContextExtensionsKt.getColorFromAttribute(getActivity(), R.attr.wpColorWarningDark);
                break;
            case UNAPPROVED:
                statusTextResId = R.string.comment_status_unapproved;
                statusColor = ContextExtensionsKt.getColorFromAttribute(getActivity(), R.attr.wpColorWarningDark);
                break;
            case SPAM:
                statusTextResId = R.string.comment_status_spam;
                statusColor = ContextExtensionsKt.getColorFromAttribute(
                        getActivity(),
                        com.google.android.material.R.attr.colorError
                );
                break;
            case TRASH:
            default:
                statusTextResId = R.string.comment_status_trash;
                statusColor = ContextExtensionsKt.getColorFromAttribute(
                        getActivity(),
                        com.google.android.material.R.attr.colorError
                );
                break;
        }

        if (mActionBinding != null && canLike()) {
            mActionBinding.btnLike.setVisibility(View.VISIBLE);
            if (mComment != null) {
                toggleLikeButton(mComment.getILike());
            } else if (mNote != null) {
                mNote.hasLikedComment();
            }
        }

        if (mBinding != null) {
            // comment status is only shown if this comment is from one of this user's blogs and the
            // comment hasn't been CommentStatus.APPROVED
            if (mIsUsersBlog && commentStatus != CommentStatus.APPROVED) {
                mBinding.textStatus.setText(getString(statusTextResId).toUpperCase(Locale.getDefault()));
                mBinding.textStatus.setTextColor(statusColor);
                if (mBinding.textStatus.getVisibility() != View.VISIBLE) {
                    mBinding.textStatus.clearAnimation();
                    AniUtils.fadeIn(mBinding.textStatus, AniUtils.Duration.LONG);
                }
            } else {
                mBinding.textStatus.setVisibility(View.GONE);
            }
        }

        if (mActionBinding != null) {
            if (canModerate()) {
                setModerateButtonForStatus(commentStatus);
                mActionBinding.btnModerate.setOnClickListener(v -> performModerateAction());
                mActionBinding.btnModerate.setVisibility(View.VISIBLE);
            } else {
                mActionBinding.btnModerate.setVisibility(View.GONE);
            }
        }

        if (mActionBinding != null) {
            if (canMarkAsSpam()) {
                mActionBinding.btnSpam.setVisibility(View.VISIBLE);
                if (commentStatus == CommentStatus.SPAM) {
                    mActionBinding.btnSpamText.setText(R.string.mnu_comment_unspam);
                } else {
                    mActionBinding.btnSpamText.setText(R.string.mnu_comment_spam);
                }
            } else {
                mActionBinding.btnSpam.setVisibility(View.GONE);
            }
        }

        if (mActionBinding != null && canTrash()) {
            if (commentStatus == CommentStatus.TRASH) {
                ColorUtils.INSTANCE.setImageResourceWithTint(
                        mActionBinding.btnModerateIcon,
                        R.drawable.ic_undo_white_24dp,
                        ContextExtensionsKt.getColorResIdFromAttribute(
                                mActionBinding.btnModerateText.getContext(),
                                com.google.android.material.R.attr.colorOnSurface
                        )
                );
                mActionBinding.btnModerateText.setText(R.string.mnu_comment_untrash);
            }
        }

        if (mActionBinding != null) {
            if (canShowMore()) {
                mActionBinding.btnMore.setVisibility(View.VISIBLE);
            } else {
                mActionBinding.btnMore.setVisibility(View.GONE);
            }
        }

        if (mActionBinding != null) {
            mActionBinding.layoutButtons.setVisibility(View.VISIBLE);
        }
    }

    private void performModerateAction() {
        if (mComment == null || !isAdded() || !NetworkUtils.checkConnection(getActivity())) {
            return;
        }

        CommentStatus newStatus = CommentStatus.APPROVED;
        CommentStatus currentStatus = CommentStatus.fromString(mComment.getStatus());
        if (currentStatus == CommentStatus.APPROVED) {
            newStatus = CommentStatus.UNAPPROVED;
        }
        announceCommentStatusChangeForAccessibility(
                currentStatus == CommentStatus.TRASH ? CommentStatus.UNTRASH : newStatus);

        setModerateButtonForStatus(newStatus);
        AniUtils.startAnimation(mActionBinding.btnModerateIcon, R.anim.notifications_button_scale);
        moderateComment(newStatus);
    }

    private void setModerateButtonForStatus(CommentStatus status) {
        if (mActionBinding != null) {
            int color;

            if (status == CommentStatus.APPROVED) {
                color = ContextExtensionsKt.getColorResIdFromAttribute(
                        mActionBinding.btnModerateText.getContext(),
                        com.google.android.material.R.attr.colorSecondary
                );
                mActionBinding.btnModerateText.setText(R.string.comment_status_approved);
                mActionBinding.btnModerateText.setAlpha(mNormalOpacity);
                mActionBinding.btnModerateIcon.setAlpha(mNormalOpacity);
            } else {
                color = ContextExtensionsKt.getColorResIdFromAttribute(
                        mActionBinding.btnModerateText.getContext(),
                        com.google.android.material.R.attr.colorOnSurface
                );
                mActionBinding.btnModerateText.setText(R.string.mnu_comment_approve);
                mActionBinding.btnModerateText.setAlpha(mMediumOpacity);
                mActionBinding.btnModerateIcon.setAlpha(mMediumOpacity);
            }

            ColorUtils.INSTANCE.setImageResourceWithTint(
                    mActionBinding.btnModerateIcon,
                    R.drawable.ic_checkmark_white_24dp, color
            );
            mActionBinding.btnModerateText.setTextColor(ContextCompat.getColor(requireContext(), color));
        }
    }

    /*
     * does user have permission to moderate/reply/spam this comment?
     */
    private boolean canModerate() {
        return mEnabledActions != null && (mEnabledActions.contains(EnabledActions.ACTION_APPROVE)
                                           || mEnabledActions.contains(EnabledActions.ACTION_UNAPPROVE));
    }

    private boolean canMarkAsSpam() {
        return (mEnabledActions != null && mEnabledActions.contains(EnabledActions.ACTION_SPAM));
    }

    private boolean canReply() {
        return (mEnabledActions != null && mEnabledActions.contains(EnabledActions.ACTION_REPLY));
    }

    private boolean canTrash() {
        return canModerate();
    }

    private boolean canEdit() {
        return mSite != null && (mSite.getHasCapabilityEditOthersPosts() || mSite.isSelfHostedAdmin());
    }

    private boolean canLike() {
        return (mEnabledActions != null && mEnabledActions.contains(EnabledActions.ACTION_LIKE)
                && mSite != null && SiteUtils.isAccessedViaWPComRest(mSite));
    }

    /*
     * The more button contains controls which only moderates can use
     */
    private boolean canShowMore() {
        return canModerate();
    }

    /*
     * display the comment associated with the passed notification
     */
    private void showCommentAsNotification(Note note, @NonNull SiteModel site, @Nullable CommentModel comment) {
        if (mBinding == null) {
            return;
        }

        // hide standard comment views, since we'll be adding note blocks instead
        mBinding.commentContent.setVisibility(View.GONE);

        mBinding.textContent.setVisibility(View.GONE);

        /*
         * determine which actions to enable for this comment - if the comment is from this user's
         * blog then all actions will be enabled, but they won't be if it's a reply to a comment
         * this user made on someone else's blog
         */
        mEnabledActions = note.getEnabledActions();

        // Set 'Reply to (Name)' in comment reply EditText if it's a reasonable size
        if (mReplyBinding != null && !TextUtils.isEmpty(mNote.getCommentAuthorName())
            && mNote.getCommentAuthorName().length() < 28) {
            mReplyBinding.editComment.setHint(
                    String.format(getString(R.string.comment_reply_to_user), mNote.getCommentAuthorName())
            );
        }

        if (comment != null) {
            setComment(comment, site);
        } else {
            setComment(note.buildComment(), site);
        }

        addDetailFragment(note.getId());

        getActivity().invalidateOptionsMenu();
    }

    /**
     * As we are using CommentDetailFragment in a ViewPager, and we also use nested fragments within
     * CommentDetailFragment itself:
     * It is important to have a live reference to the Comment Container layout at the moment this layout is
     * inflated (onCreateView), so we can make sure we set its ID correctly once we have an actual Comment object
     * to populate it with. Otherwise, we could be searching and finding the container for _another fragment/page
     * in the viewpager_, which would cause strange results (changing the views for a different fragment than we
     * intended to).
     * <p>
     * FYI: The above comment is slightly outdated as the Comment Container layout is now a binding, there is no live
     * reference the Comment Container layout anymore.
     */
    private void addDetailFragment(String noteId) {
        if (mBinding != null && mActionBinding != null) {
            // Now we'll add a detail fragment list
            FragmentManager fragmentManager = getChildFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            mNotificationsDetailListFragment = NotificationsDetailListFragment.newInstance(noteId);
            mNotificationsDetailListFragment.setFooterView(mActionBinding.layoutButtons);
            fragmentTransaction.replace(mBinding.commentContentContainer.getId(), mNotificationsDetailListFragment);
            fragmentTransaction.commitAllowingStateLoss();
        }
    }

    // Like or unlike a comment via the REST API
    private void likeComment(boolean forceLike) {
        if (mActionBinding == null || !isAdded()) {
            return;
        }
        if (forceLike && mActionBinding.btnLike.isActivated()) {
            return;
        }

        toggleLikeButton(!mActionBinding.btnLike.isActivated());

        ReaderAnim.animateLikeButton(mActionBinding.btnLikeIcon, mActionBinding.btnLike.isActivated());

        // Bump analytics
        // TODO klymyam remove legacy comment tracking after new comments are shipped and new funnels are made
        if (mCommentSource == CommentSource.NOTIFICATION) {
            AnalyticsTracker.track(
                    mActionBinding.btnLike.isActivated() ? Stat.NOTIFICATION_LIKED : Stat.NOTIFICATION_UNLIKED
            );
        }
        AnalyticsUtils.trackCommentActionWithSiteDetails(
                mActionBinding.btnLike.isActivated() ? Stat.COMMENT_LIKED : Stat.COMMENT_UNLIKED,
                mCommentSource.toAnalyticsCommentActionSource(), mSite);

        if (mNotificationsDetailListFragment != null && mComment != null) {
            // Optimistically set comment to approved when liking an unapproved comment
            // WP.com will set a comment to approved if it is liked while unapproved
            if (mActionBinding.btnLike.isActivated()
                && CommentStatus.fromString(mComment.getStatus()) == CommentStatus.UNAPPROVED) {
                mComment.setStatus(CommentStatus.APPROVED.toString());
                mNotificationsDetailListFragment.refreshBlocksForCommentStatus(CommentStatus.APPROVED);
                setModerateButtonForStatus(CommentStatus.APPROVED);
            }
        }
        mCommentsStoreAdapter.dispatch(CommentActionBuilder.newLikeCommentAction(
                new RemoteLikeCommentPayload(mSite, mComment, mActionBinding.btnLike.isActivated()))
        );
        mActionBinding.btnLike.announceForAccessibility(
                getText(mActionBinding.btnLike.isActivated() ? R.string.comment_liked_talkback
                        : R.string.comment_unliked_talkback)
        );
    }

    private void toggleLikeButton(boolean isLiked) {
        if (mActionBinding != null) {
            int color;
            int drawable;

            if (isLiked) {
                color = ContextExtensionsKt.getColorResIdFromAttribute(
                        mActionBinding.btnLikeIcon.getContext(),
                        com.google.android.material.R.attr.colorSecondary
                );
                drawable = R.drawable.ic_star_white_24dp;
                mActionBinding.btnLikeText.setText(getResources().getString(R.string.mnu_comment_liked));
                mActionBinding.btnLike.setActivated(true);
                mActionBinding.btnLikeText.setAlpha(mNormalOpacity);
                mActionBinding.btnLikeIcon.setAlpha(mNormalOpacity);
            } else {
                color = ContextExtensionsKt.getColorResIdFromAttribute(
                        mActionBinding.btnLikeIcon.getContext(),
                        com.google.android.material.R.attr.colorOnSurface
                );
                drawable = R.drawable.ic_star_outline_white_24dp;
                mActionBinding.btnLikeText.setText(getResources().getString(R.string.reader_label_like));
                mActionBinding.btnLike.setActivated(false);
                mActionBinding.btnLikeText.setAlpha(mMediumOpacity);
                mActionBinding.btnLikeIcon.setAlpha(mMediumOpacity);
            }

            ColorUtils.INSTANCE.setImageResourceWithTint(mActionBinding.btnLikeIcon, drawable, color);
            mActionBinding.btnLikeText.setTextColor(ContextCompat.getColor(requireContext(), color));
        }
    }

    private void setProgressVisible(boolean visible) {
        final ProgressBar progress = (isAdded() && mBinding != null ? (ProgressBar) mBinding.progressLoading : null);
        if (progress != null) {
            progress.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void onCommentModerated(OnCommentChanged event) {
        // send signal for listeners to perform any needed updates
        if (mNote != null) {
            EventBus.getDefault().postSticky(new NotificationEvents.NoteLikeOrModerationStatusChanged(mNote.getId()));
        }

        if (!isAdded()) {
            return;
        }

        if (event.isError()) {
            mComment.setStatus(mPreviousStatus);
            updateStatusViews();
            ToastUtils.showToast(getActivity(), R.string.error_moderate_comment);
        } else {
            reloadComment();
        }
    }

    private void onCommentCreated(OnCommentChanged event) {
        if (mReplyBinding != null) {
            mIsSubmittingReply = false;
            mReplyBinding.editComment.setEnabled(true);
            mReplyBinding.btnSubmitReply.setVisibility(View.VISIBLE);
            mReplyBinding.progressSubmitComment.setVisibility(View.GONE);
            updateStatusViews();

            if (event.isError()) {
                if (isAdded()) {
                    String strUnEscapeHTML = StringEscapeUtils.unescapeHtml4(event.error.message);
                    ToastUtils.showToast(getActivity(), strUnEscapeHTML, ToastUtils.Duration.LONG);
                    // refocus editor on failure and show soft keyboard
                    EditTextUtils.showSoftInput(mReplyBinding.editComment);
                }
                return;
            }

            reloadComment();

            if (isAdded()) {
                ToastUtils.showToast(getActivity(), getString(R.string.note_reply_successful));
                mReplyBinding.editComment.setText(null);
                mReplyBinding.editComment.getAutoSaveTextHelper().clearSavedText(mReplyBinding.editComment);
            }

            // Self Hosted site does not return a newly created comment, so we need to fetch it manually.
            if (!mSite.isUsingWpComRestApi() && !event.changedCommentsLocalIds.isEmpty()) {
                CommentModel createdComment =
                        mCommentsStoreAdapter.getCommentByLocalId(event.changedCommentsLocalIds.get(0));

                if (createdComment != null) {
                    mCommentsStoreAdapter.dispatch(CommentActionBuilder.newFetchCommentAction(
                            new RemoteCommentPayload(mSite, createdComment.getRemoteCommentId())));
                }
            }

            // approve the comment
            if (mComment != null && !(CommentStatus.fromString(mComment.getStatus()) == CommentStatus.APPROVED)) {
                moderateComment(CommentStatus.APPROVED);
            }
        }
    }

    private void onCommentLiked(OnCommentChanged event) {
        // send signal for listeners to perform any needed updates
        if (mNote != null) {
            EventBus.getDefault().postSticky(new NotificationEvents.NoteLikeOrModerationStatusChanged(mNote.getId()));
        }

        if (mActionBinding != null && event.isError()) {
            // Revert button state in case of an error
            toggleLikeButton(!mActionBinding.btnLike.isActivated());
        }
    }

    // OnChanged events

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    @OptIn(markerClass = DelicateCoroutinesApi.class)
    public void onCommentChanged(OnCommentChanged event) {
        setProgressVisible(false);
        // requesting local comment cache refresh
        BuildersKt.launch(GlobalScope.INSTANCE,
                Dispatchers.getMain(),
                CoroutineStart.DEFAULT,
                (coroutineScope, continuation) -> mLocalCommentCacheUpdateHandler.requestCommentsUpdate(continuation)

        );
        // Moderating comment
        if (event.causeOfChange == CommentAction.PUSH_COMMENT) {
            onCommentModerated(event);
            mPreviousStatus = null;
            return;
        }

        // New comment (reply)
        if (event.causeOfChange == CommentAction.CREATE_NEW_COMMENT) {
            onCommentCreated(event);
            return;
        }

        // Like/Unlike
        if (event.causeOfChange == CommentAction.LIKE_COMMENT) {
            onCommentLiked(event);
            return;
        }

        if (event.isError()) {
            AppLog.i(T.TESTS, "event error type: " + event.error.type + " - message: " + event.error.message);
            if (isAdded() && !TextUtils.isEmpty(event.error.message)) {
                ToastUtils.showToast(getActivity(), event.error.message);
            }
            return;
        }
    }

    private void announceCommentStatusChangeForAccessibility(CommentStatus newStatus) {
        int resId = -1;
        switch (newStatus) {
            case APPROVED:
                resId = R.string.comment_approved_talkback;
                break;
            case UNAPPROVED:
                resId = R.string.comment_unapproved_talkback;
                break;
            case SPAM:
                resId = R.string.comment_spam_talkback;
                break;
            case TRASH:
                resId = R.string.comment_trash_talkback;
                break;
            case DELETED:
                resId = R.string.comment_delete_talkback;
                break;
            case UNSPAM:
                resId = R.string.comment_unspam_talkback;
                break;
            case UNTRASH:
                resId = R.string.comment_untrash_talkback;
                break;
            case ALL:
                // ignore
                break;
            default:
                AppLog.w(T.COMMENTS,
                        "AnnounceCommentStatusChangeForAccessibility - Missing switch branch for comment status: "
                        + newStatus);
        }
        if (resId != -1 && getView() != null) {
            getView().announceForAccessibility(getText(resId));
        }
    }

    // Handle More Menu
    private void showMoreMenu(View view) {
        androidx.appcompat.widget.PopupMenu morePopupMenu =
                new androidx.appcompat.widget.PopupMenu(requireContext(), view);
        morePopupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit) {
                editComment();
                return true;
            }
            if (item.getItemId() == R.id.action_trash) {
                trashComment();
                return true;
            }
            if (item.getItemId() == R.id.action_copy_link_address) {
                copyCommentLinkAddress();
                return true;
            }
            return false;
        });

        morePopupMenu.inflate(R.menu.menu_comment_more);

        MenuItem trashMenuItem = morePopupMenu.getMenu().findItem(R.id.action_trash);
        MenuItem copyLinkAddress = morePopupMenu.getMenu().findItem(R.id.action_copy_link_address);
        if (canTrash()) {
            CommentStatus commentStatus = CommentStatus.fromString(mComment.getStatus());
            if (commentStatus == CommentStatus.TRASH) {
                copyLinkAddress.setVisible(false);
                trashMenuItem.setTitle(R.string.mnu_comment_delete_permanently);
            } else {
                trashMenuItem.setTitle(R.string.mnu_comment_trash);
                if (commentStatus == CommentStatus.SPAM) {
                    copyLinkAddress.setVisible(false);
                } else {
                    copyLinkAddress.setVisible(true);
                }
            }
        } else {
            trashMenuItem.setVisible(false);
            copyLinkAddress.setVisible(false);
        }

        MenuItem editMenuItem = morePopupMenu.getMenu().findItem(R.id.action_edit);
        editMenuItem.setVisible(false);
        if (canEdit()) {
            editMenuItem.setVisible(true);
        }
        morePopupMenu.show();
    }

    private void trashComment() {
        if (!isAdded() || mComment == null) {
            return;
        }

        CommentStatus status = CommentStatus.fromString(mComment.getStatus());
        // If the comment status is trash or spam, next deletion is a permanent deletion.
        if (status == CommentStatus.TRASH || status == CommentStatus.SPAM) {
            AlertDialog.Builder dialogBuilder = new MaterialAlertDialogBuilder(getActivity());
            dialogBuilder.setTitle(getResources().getText(R.string.delete));
            dialogBuilder.setMessage(getResources().getText(R.string.dlg_sure_to_delete_comment));
            dialogBuilder.setPositiveButton(getResources().getText(R.string.yes),
                    (dialog, whichButton) -> {
                        moderateComment(CommentStatus.DELETED);
                        announceCommentStatusChangeForAccessibility(CommentStatus.DELETED);
                    });
            dialogBuilder.setNegativeButton(
                    getResources().getText(R.string.no),
                    null);
            dialogBuilder.setCancelable(true);
            dialogBuilder.create().show();
        } else {
            moderateComment(CommentStatus.TRASH);
            announceCommentStatusChangeForAccessibility(CommentStatus.TRASH);
        }
    }

    private void copyCommentLinkAddress() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("CommentLinkAddress", mComment.getUrl()));
            showSnackBar(getString(R.string.comment_q_action_copied_url));
        } catch (Exception e) {
            AppLog.e(T.UTILS, e);
            showSnackBar(getString(R.string.error_copy_to_clipboard));
        }
    }

    private void showSnackBar(String message) {
        if (mBinding != null) {
            Snackbar snackBar = WPSnackbar.make(mBinding.getRoot(), message, Snackbar.LENGTH_LONG)
                                          .setAction(getString(R.string.share_action),
                                                  v -> {
                                                      try {
                                                          Intent intent = new Intent(Intent.ACTION_SEND);
                                                          intent.setType("text/plain");
                                                          intent.putExtra(Intent.EXTRA_TEXT, mComment.getUrl());
                                                          startActivity(Intent.createChooser(intent,
                                                                  getString(R.string.comment_share_link_via)));
                                                      } catch (ActivityNotFoundException exception) {
                                                          ToastUtils.showToast(mBinding.getRoot().getContext(),
                                                                  R.string.comment_toast_err_share_intent);
                                                      }
                                                  })
                                          .setAnchorView(mBinding.layoutBottom);
            snackBar.show();
        }
    }

    @Nullable
    @Override
    public View getScrollableViewForUniqueIdProvision() {
        if (mBinding != null) {
            return mBinding.nestedScrollView;
        } else {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
        mReplyBinding = null;
        mActionBinding = null;
    }
}
