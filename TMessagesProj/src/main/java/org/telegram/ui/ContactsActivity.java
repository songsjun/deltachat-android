/*
 * This part of the Delta Chat fronted is based on Telegram which is covered by the following note:
 *
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MrMailbox;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.Adapters.ContactsAdapter;
import org.telegram.ui.Adapters.SearchAdapter;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;


public class ContactsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private BaseFragmentAdapter listViewAdapter;
    private TextView emptyTextView;
    private ListView listView;

    //private SearchAdapter searchListViewAdapter;
    //private boolean searchWas;
    //private boolean searching;

    private boolean returnAsResult;
    private boolean needForwardCount = true;
    private int chat_id;
    private String selectAlertString = null;
    private boolean allowUsernameSearch = true;
    private ContactsActivityDelegate delegate;

    private String title;
    private String subtitle;

    private final static int id_add_contact = 2;
    private final static int id_new_group   = 3;

    public interface ContactsActivityDelegate {
        void didSelectContact(TLRPC.User user, String param);
    }

    public ContactsActivity(Bundle args) {
        super(args);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.closeChats);
        if (arguments != null) {

            returnAsResult = arguments.getBoolean("returnAsResult", false);
            selectAlertString = arguments.getString("selectAlertString");
            allowUsernameSearch = arguments.getBoolean("allowUsernameSearch", true);
            needForwardCount = arguments.getBoolean("needForwardCount", true);
            chat_id = arguments.getInt("chat_id", 0);

            if( arguments.getBoolean("do_create_new_chat", false) ) {
                title = LocaleController.getString("NewChat", R.string.NewChat);
                subtitle = LocaleController.getString("SendMessageTo", R.string.SendMessageTo);
            }


        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.contactsDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.closeChats);
        delegate = null;
    }

    @Override
    public View createView(Context context) {

        //searching = false;
        //searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if( title != null ) {
            actionBar.setTitle(title);
            if( subtitle != null ) {
                actionBar.setSubtitle(subtitle);
            }
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
                else if( id == id_new_group ) {
                    presentFragment(new GroupCreateActivity(), true);
                }
                else if( id == id_add_contact ) {
                    Toast.makeText(getParentActivity(), LocaleController.getString("NotYetImplemented", R.string.NotYetImplemented), Toast.LENGTH_LONG).show();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem item = menu.addItem(10, R.drawable.ic_ab_other);
        item.addSubItem(id_add_contact, LocaleController.getString("AddContactTitle", R.string.AddContactTitle), 0);
        item.addSubItem(id_new_group, LocaleController.getString("NewGroup", R.string.NewGroup), 0);
        /*
        ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
            }

            @Override
            public void onSearchCollapse() {
                searchListViewAdapter.searchDialogs(null);
                searching = false;
                searchWas = false;
                listView.setAdapter(listViewAdapter);
                listViewAdapter.notifyDataSetChanged();
                listView.setFastScrollAlwaysVisible(true);
                listView.setFastScrollEnabled(true);
                listView.setVerticalScrollBarEnabled(false);
                emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
            }

            @Override
            public void onTextChanged(EditText editText) {
                if (searchListViewAdapter == null) {
                    return;
                }
                String text = editText.getText().toString();
                if (text.length() != 0) {
                    searchWas = true;
                    if (listView != null) {
                        listView.setAdapter(searchListViewAdapter);
                        searchListViewAdapter.notifyDataSetChanged();
                        listView.setFastScrollAlwaysVisible(false);
                        listView.setFastScrollEnabled(false);
                        listView.setVerticalScrollBarEnabled(true);
                    }
                    if (emptyTextView != null) {
                        emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                    }
                }
                searchListViewAdapter.searchDialogs(text);
            }
        });
        item.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));

        searchListViewAdapter = new SearchAdapter(context, null, allowUsernameSearch, false, false, true);
        */
        listViewAdapter = new ContactsAdapter(context);

        fragmentView = new FrameLayout(context);

        LinearLayout emptyTextLayout = new LinearLayout(context);
        emptyTextLayout.setVisibility(View.INVISIBLE);
        emptyTextLayout.setOrientation(LinearLayout.VERTICAL);
        ((FrameLayout) fragmentView).addView(emptyTextLayout);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emptyTextLayout.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP;
        emptyTextLayout.setLayoutParams(layoutParams);
        emptyTextLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        emptyTextView = new TextView(context);
        emptyTextView.setTextColor(0xff808080);
        emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
        emptyTextLayout.addView(emptyTextView);
        LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) emptyTextView.getLayoutParams();
        layoutParams1.width = LayoutHelper.MATCH_PARENT;
        layoutParams1.height = LayoutHelper.MATCH_PARENT;
        layoutParams1.weight = 0.5f;
        emptyTextView.setLayoutParams(layoutParams1);

        FrameLayout frameLayout = new FrameLayout(context);
        emptyTextLayout.addView(frameLayout);
        layoutParams1 = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
        layoutParams1.width = LayoutHelper.MATCH_PARENT;
        layoutParams1.height = LayoutHelper.MATCH_PARENT;
        layoutParams1.weight = 0.5f;
        frameLayout.setLayoutParams(layoutParams1);

        listView = new ListView(context);
        listView.setEmptyView(emptyTextLayout);
        listView.setVerticalScrollBarEnabled(false);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setFastScrollEnabled(true);
        listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        listView.setAdapter(listViewAdapter);
        listView.setFastScrollAlwaysVisible(true);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
        ((FrameLayout) fragmentView).addView(listView);
        layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.MATCH_PARENT;
        listView.setLayoutParams(layoutParams);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                /*
                if (searching && searchWas) {
                    TLRPC.User user = (TLRPC.User) searchListViewAdapter.getItem(i);
                    if (user == null) {
                        return;
                    }
                    if (searchListViewAdapter.isGlobalSearch(i)) {
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        //MessagesController.getInstance().putUsers(users, false);
                        //MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                    }
                    if (returnAsResult) {
                        didSelectResult(user, true, null);
                    } else {
                        Bundle args = new Bundle();
                        args.putInt("user_id", user.id);
                        presentFragment(new ChatActivity(args), true);
                    }
                } else*/ {
                    Object item = listViewAdapter.getItem(i);
                    if (item instanceof TLRPC.User) {
                        final TLRPC.User user = (TLRPC.User) item;
                        if (returnAsResult) {
                            didSelectResult(user, true, null);
                        } else {
                            int belonging_chat_id = MrMailbox.MrMailboxGetChatIdByContactId(MrMailbox.hMailbox, user.id);
                            if( belonging_chat_id!=0 ) {
                                Bundle args = new Bundle();
                                args.putInt("chat_id", belonging_chat_id);
                                presentFragment(new ChatActivity(args), true);
                                return;
                            }

                            long hContact = MrMailbox.MrMailboxGetContact(MrMailbox.hMailbox, user.id);
                                String name = MrMailbox.MrContactGetNameNAddr(hContact);
                            MrMailbox.MrContactUnref(hContact);

                            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    int belonging_chat_id = MrMailbox.MrMailboxCreateChatByContactId(MrMailbox.hMailbox, user.id);
                                    if( belonging_chat_id != 0 ) {
                                        Bundle args = new Bundle();
                                        args.putInt("chat_id", belonging_chat_id);
                                        presentFragment(new ChatActivity(args), true);
                                        return;
                                    }
                                }
                            });
                            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("AskStartChatWith", R.string.AskStartChatWith, name)));
                            showDialog(builder.create());
                        }
                    }
                }
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
                /*if (i == SCROLL_STATE_TOUCH_SCROLL && searching && searchWas) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }*/
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (absListView.isFastScrollEnabled()) {
                    AndroidUtilities.clearDrawableAnimation(absListView);
                }
            }
        });

        return fragmentView;
    }

    private void didSelectResult(final TLRPC.User user, boolean useAlert, String param) {
        if (useAlert && selectAlertString != null) {
            if (getParentActivity() == null) {
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            String message = LocaleController.formatStringSimple(selectAlertString, UserObject.getUserName(user));
            EditText editText = null;
            if ( needForwardCount ) {
                message = String.format("%s\n\n%s", message, LocaleController.getString("AddToTheGroupForwardCount", R.string.AddToTheGroupForwardCount));
                editText = new EditText(getParentActivity());
                editText.setTextSize(18);
                editText.setText("50");
                editText.setGravity(Gravity.CENTER);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
                final EditText editTextFinal = editText;
                editText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {

                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        try {
                            String str = s.toString();
                            if (str.length() != 0) {
                                int value = Utilities.parseInt(str);
                                if (value < 0) {
                                    editTextFinal.setText("0");
                                    editTextFinal.setSelection(editTextFinal.length());
                                } else if (value > 300) {
                                    editTextFinal.setText("300");
                                    editTextFinal.setSelection(editTextFinal.length());
                                } else if (!str.equals("" + value)) {
                                    editTextFinal.setText("" + value);
                                    editTextFinal.setSelection(editTextFinal.length());
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }

                });
                builder.setView(editText);
            }
            builder.setMessage(message);
            final EditText finalEditText = editText;
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    didSelectResult(user, false, finalEditText != null ? finalEditText.getText().toString() : "0");
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
            if (editText != null) {
                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
                if (layoutParams != null) {
                    if (layoutParams instanceof FrameLayout.LayoutParams) {
                        ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
                    }
                    layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(10);
                    editText.setLayoutParams(layoutParams);
                }
                editText.setSelection(editText.getText().length());
            }
        } else {
            if (delegate != null) {
                delegate.didSelectContact(user, param);
                delegate = null;
            }
            finishFragment();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (actionBar != null) {
            actionBar.closeSearchField();
        }
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.contactsDidLoaded) {
            if (listViewAdapter != null) {
                listViewAdapter.notifyDataSetChanged();
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer)args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == NotificationCenter.closeChats) {
            removeSelfFromStack();
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView != null) {
            int count = listView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = listView.getChildAt(a);
                if (child instanceof UserCell) {
                    ((UserCell) child).update(mask);
                }
            }
        }
    }

    public void setDelegate(ContactsActivityDelegate delegate) {
        this.delegate = delegate;
    }

}
