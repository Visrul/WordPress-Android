/**
 * One fragment to rule them all (Notes, that is)
 */
package org.wordpress.android.ui.notifications;

import android.app.ListFragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wordpress.android.R;
import org.wordpress.android.models.CommentStatus;
import org.wordpress.android.models.Note;
import org.wordpress.android.ui.notifications.blocks.CommentUserNoteBlock;
import org.wordpress.android.ui.notifications.blocks.HeaderUserNoteBlock;
import org.wordpress.android.ui.notifications.blocks.NoteBlock;
import org.wordpress.android.ui.notifications.blocks.NoteBlockClickableSpan;
import org.wordpress.android.ui.notifications.blocks.NoteBlockRangeType;
import org.wordpress.android.ui.notifications.blocks.UserNoteBlock;
import org.wordpress.android.ui.notifications.utils.NotificationsUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.JSONUtil;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public class NotificationsDetailListFragment extends ListFragment implements NotificationFragment {
    private Note mNote;
    private List<NoteBlock> mNoteBlockArray = new ArrayList<NoteBlock>();
    private LinearLayout mRootLayout;
    private ViewGroup mFooterView;

    private int mBackgroundColor;
    private CommentUserNoteBlock.OnCommentStatusChangeListener mOnCommentStatusChangeListener;

    public NotificationsDetailListFragment() {
    }

    public static NotificationsDetailListFragment newInstance(final Note note) {
        NotificationsDetailListFragment fragment = new NotificationsDetailListFragment();
        fragment.setNote(note);
        return fragment;
    }

    @Override
    public View onCreateView(@Nonnull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.notifications_fragment_detail_list, container, false);
        mRootLayout = (LinearLayout)view.findViewById(R.id.notifications_list_root);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
        super.onActivityCreated(bundle);

        mBackgroundColor = getResources().getColor(R.color.white);

        ListView listView = getListView();
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setHeaderDividersEnabled(false);

        if (mFooterView != null) {
           listView.addFooterView(mFooterView);
        }

        reloadNoteBlocks();
    }

    @Override
    public Note getNote() {
        return mNote;
    }

    @Override
    public void setNote(Note note) {
        mNote = note;
    }

    public void reloadNoteBlocks() {
        new LoadNoteBlocksTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void setFooterView(ViewGroup footerView) {
        mFooterView = footerView;
    }

    public void refreshBlocksForCommentStatus(CommentStatus newStatus) {
        if (mOnCommentStatusChangeListener != null) {
            mOnCommentStatusChangeListener.onCommentStatusChanged(newStatus);
            NoteBlockAdapter noteBlockAdapter = (NoteBlockAdapter)getListAdapter();
            noteBlockAdapter.notifyDataSetChanged();
        }
    }

    private class NoteBlockAdapter extends ArrayAdapter<NoteBlock> {

        private List<NoteBlock> mNoteBlockList;
        private LayoutInflater mLayoutInflater;

        NoteBlockAdapter(Context context, List<NoteBlock> noteBlocks) {
            super(context, 0, noteBlocks);

            mNoteBlockList = noteBlocks;
            mLayoutInflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            NoteBlock noteBlock = mNoteBlockList.get(position);

            // Check the tag for this recycled view, if it matches we can reuse it
            if (convertView == null || noteBlock.getBlockType() != convertView.getTag(R.id.note_block_tag_id)) {
                convertView = mLayoutInflater.inflate(noteBlock.getLayoutResourceId(), parent, false);
                convertView.setTag(noteBlock.getViewHolder(convertView));
            }

            // Update the block type for this view
            convertView.setTag(R.id.note_block_tag_id, noteBlock.getBlockType());

            noteBlock.setBackgroundColor(mBackgroundColor);

            return noteBlock.configureView(convertView);
        }
    }

    private NoteBlock.OnNoteBlockTextClickListener mOnNoteBlockTextClickListener = new NoteBlock.OnNoteBlockTextClickListener() {
        @Override
        public void onNoteBlockTextClicked(NoteBlockClickableSpan clickedSpan) {
            if (!isAdded()) return;

            NotificationsUtils.handleNoteBlockSpanClick((NotificationsActivity) getActivity(), clickedSpan);
        }

        @Override
        public void showReaderPostForNoteIds() {
            if (!isAdded() || mNote == null) return;

            NotificationsActivity notificationsActivity = (NotificationsActivity)getActivity();
            notificationsActivity.showPostForSiteAndPostId(mNote.getSiteId(), mNote.getPostId());
        }
    };

    private UserNoteBlock.OnGravatarClickedListener mOnGravatarClickedListener = new UserNoteBlock.OnGravatarClickedListener() {
        @Override
        public void onGravatarClicked(long siteId, long userId) {
            if (!isAdded()) return;

            NotificationsActivity notificationsActivity = (NotificationsActivity)getActivity();
            notificationsActivity.showBlogPreviewForSiteId(siteId, null);
        }
    };


    // Loop through the 'body' items in this note, and create blocks for each.
    private class LoadNoteBlocksTask extends AsyncTask<Void, Boolean, Boolean> {

        @Override
        protected Boolean doInBackground(Void... params) {
            if (mNote == null) return false;

            JSONArray bodyArray = mNote.getBody();
            mNoteBlockArray.clear();

            // Add the note header if one was provided
            if (mNote.getHeader() != null) {
                HeaderUserNoteBlock headerNoteBlock = new HeaderUserNoteBlock(
                        mNote.getHeader(),
                        mOnNoteBlockTextClickListener,
                        mOnGravatarClickedListener
                );

                headerNoteBlock.setIsComment(mNote.isCommentType());
                mNoteBlockArray.add(headerNoteBlock);
            }

            boolean isBadgeView = false;
            if (bodyArray != null && bodyArray.length() > 0) {
                for (int i=0; i < bodyArray.length(); i++) {
                    try {
                        JSONObject noteObject = bodyArray.getJSONObject(i);
                        // Determine NoteBlock type and add it to the array
                        NoteBlock noteBlock;
                        String noteBlockTypeString = JSONUtil.queryJSON(noteObject, "type", "");

                        if (NoteBlockRangeType.fromString(noteBlockTypeString) == NoteBlockRangeType.USER) {
                            if (mNote.isCommentType()) {
                                // We'll snag the next body array item for comment user blocks
                                if (i + 1 < bodyArray.length()) {
                                    JSONObject commentTextBlock = bodyArray.getJSONObject(i + 1);
                                    noteObject.put("comment_text", commentTextBlock);
                                    i++;
                                }

                                // Add timestamp to block for display
                                noteObject.put("timestamp", mNote.getTimestamp());

                                noteBlock = new CommentUserNoteBlock(
                                        noteObject,
                                        mOnNoteBlockTextClickListener,
                                        mOnGravatarClickedListener
                                );

                                // Set listener for comment status changes, so we can update bg and text colors
                                CommentUserNoteBlock commentUserNoteBlock = (CommentUserNoteBlock)noteBlock;
                                mOnCommentStatusChangeListener = commentUserNoteBlock.getOnCommentChangeListener();
                                commentUserNoteBlock.setCommentStatus(mNote.getCommentStatus());
                                commentUserNoteBlock.configureResources(getActivity());
                            } else {
                                noteBlock = new UserNoteBlock(
                                        noteObject,
                                        mOnNoteBlockTextClickListener,
                                        mOnGravatarClickedListener
                                );
                            }
                        } else {
                            noteBlock = new NoteBlock(noteObject, mOnNoteBlockTextClickListener);
                        }

                        // Badge notifications apply different colors and formatting
                        if (isAdded() && noteBlock.containsBadgeMediaType()) {
                            isBadgeView = true;
                            mBackgroundColor = getActivity().getResources().getColor(R.color.transparent);
                        }

                        mNoteBlockArray.add(noteBlock);
                    } catch (JSONException e) {
                        AppLog.e(AppLog.T.NOTIFS, "Invalid note data, could not parse.");
                    }
                }
            }

            return isBadgeView;
        }

        @Override
        protected void onPostExecute(Boolean isBadgeView) {
            if (!isAdded()) return;

            if (isBadgeView) {
                mRootLayout.setGravity(Gravity.CENTER_VERTICAL);
            }

            setListAdapter(new NoteBlockAdapter(getActivity(), mNoteBlockArray));
        }
    }
}
