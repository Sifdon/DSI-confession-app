package com.dark.confess.Helpers;

import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.dark.confess.Models.Post;
import com.dark.confess.Models.Reply;
import com.dark.confess.Utilities.Constants;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static android.content.ContentValues.TAG;

/**
 * Created by darshan on 29/09/16.
 */

public class FireBaseHelper {

    private DatabaseReference databaseReference;
    Context context;


    public FireBaseHelper(DatabaseReference databaseReference, Context context) {
        this.databaseReference = databaseReference;
        this.context = context;
    }

    public void writeNewPost(String userId, String username, String body) {

        ArrayList<String> hashTagsList = Constants.getHashTags(body);

        String key = databaseReference.child(Constants.POSTS).push().getKey();

        Post post = new Post(userId, username, body, Constants.getCurrentTime());
        Map<String, Object> postValues = post.toMap();

        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/" + Constants.POSTS + "/" + key, postValues);


        for (String hashTag : hashTagsList) {
//            databaseReference.child(Constants.HASH_TAGS).child(hashTag).child(key).updateChildren(postValues);
            childUpdates.put("/" + Constants.HASH_TAGS + "/" + hashTag + "/" + key, postValues);
        }

        databaseReference.updateChildren(childUpdates);

//        addIdToHashTags(hashTagsList, key, postValues);

    }


    //comment is reply
    public void writeReply(String postId, String uid, String name, String replyValue) {

        String key = databaseReference.child(Constants.REPLIES).push().getKey();

        Reply reply = new Reply(name, uid, replyValue, Constants.getCurrentTime());
        Map<String, Object> replyValuesMap = reply.toMap();

        Map<String, Object> childUpdate = new HashMap<>();
        childUpdate.put("/" + Constants.REPLIES + "/" + postId + "/" + key, replyValuesMap);

        databaseReference.updateChildren(childUpdate);

    }

    public void deletePost(String postId, String uid, final CallBack deletePostCallBack) {

        databaseReference.child(Constants.POSTS).child(postId).removeValue().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                deletePostCallBack.onComplete(true);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                deletePostCallBack.onComplete(false);
            }
        });


    }

    //same function without callback
    public void deletePost(String postId, String uid) {

        databaseReference.child(Constants.POSTS).child(postId).removeValue();

    }


    public void deleteReply(String postId, String replyId, String uid, final CallBack deleteCallBack) {

        databaseReference.child(Constants.REPLIES).child(postId).child(replyId).removeValue().addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                deleteCallBack.onComplete(false);
            }
        }).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                deleteCallBack.onComplete(true);
            }
        });
    }

    //delete reply/comment without callback
    public void deleteReply(String postId, String replyId, String uid) {
        databaseReference.child(Constants.REPLIES).child(postId).child(replyId).removeValue();
    }

    public boolean likeOrUnlikePost(String postId, final String uid) {

        DatabaseReference postRef = databaseReference.child(Constants.POSTS).child(postId);

        postRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                Post post = mutableData.getValue(Post.class);
                if (post == null) {
                    return Transaction.success(mutableData);
                }

                if (post.getStars().containsKey(uid)) {
                    // Unstar the post and remove self from stars
                    post.setStarCount(post.getStarCount() - 1);
                    post.getStars().remove(uid);
                } else {
                    // Star the post and add self to stars
                    post.setStarCount(post.getStarCount() + 1);
                    post.getStars().put(uid, true);
                }

                // Set value and report transaction success
                mutableData.setValue(post);
                return Transaction.success(mutableData);
            }

            @Override
            public void onComplete(DatabaseError databaseError, boolean b,
                                   DataSnapshot dataSnapshot) {
                // Transaction completed
                Log.d(TAG, "postTransaction:onComplete:" + databaseError);
            }
        });

        return true;
    }

    //fetch posts,fetch comments

    public ArrayList<Post> fetchPosts(final PostsFetched postsFetched, String type) {

        final ArrayList<Post> postArrayList = new ArrayList<>();
        Query dbRef;

        if (type.equals(Constants.POPULAR))
            dbRef = databaseReference.child(Constants.POSTS).orderByChild("starCount");
        else
            dbRef = databaseReference.child(Constants.POSTS).orderByChild("time");

        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                for (DataSnapshot postSnapShot : dataSnapshot.getChildren()) {

                    Log.d(TAG, "onDataChange: " + postSnapShot);
                    Post post = postSnapShot.getValue(Post.class);
                    postArrayList.add(post);

                }

                //reverse the arrayList
                Collections.reverse(postArrayList);

                //callback to notify that the data is fetched
                postsFetched.onPostsFetched(postArrayList);

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });


        return postArrayList;
    }

    public void searchPostsWithHashTag(String hashTag, final PostsFetched postsFetched) {
        final ArrayList<Post> postArrayList = new ArrayList<>();

        databaseReference.child(Constants.HASH_TAGS).child(hashTag).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                for (DataSnapshot postSnapShot : dataSnapshot.getChildren()) {
                    Log.d(TAG, "onDataChange: " + postSnapShot);
                    Post post = postSnapShot.getValue(Post.class);
                    postArrayList.add(post);
                }
                //callback to notify that the data is fetched
                postsFetched.onPostsFetched(postArrayList);

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });


    }


    public ArrayList<Reply> fetchComments(String postID, final RepliesFetched repliesFetched) {

        final ArrayList<Reply> replyArrayList = new ArrayList<>();

        databaseReference.child(Constants.REPLIES).child(postID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                for (DataSnapshot replySnapShot : dataSnapshot.getChildren()) {
                    Reply reply = replySnapShot.getValue(Reply.class);
                    replyArrayList.add(reply);
                }

                repliesFetched.onRepliesFetched(replyArrayList);

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

        return replyArrayList;

    }

    public boolean reportPost(String postId, final CallBack callBack) {

        databaseReference.child(Constants.REPORTED_POSTS).child(postId).setValue(true)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        callBack.onComplete(true);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        callBack.onComplete(false);
                    }
                });

        return true;
    }

    public boolean reportReply(String replyId, final CallBack callBack) {

        databaseReference.child(Constants.REPORTED_REPLIES).child(replyId).setValue(true)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        callBack.onComplete(true);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        callBack.onComplete(false);
                    }
                });

        return true;
    }

    public boolean setUserName(final String name, String uid, final CallBack callBack) {

        databaseReference.child(Constants.USERS).child(uid).setValue(name)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        callBack.onComplete(true);
                        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(Constants.USER_NAME, name).apply();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        callBack.onComplete(false);
                    }
                });
        return true;
    }


   /* private void addIdToHashTags(ArrayList<String> hashTagsList, String key, Map<String, Object> postValues) {

//        HashMap<String, Object> postIdHashMap = new HashMap<>();
//        postIdHashMap.put(key, true);

        for (String hashTag : hashTagsList) {
            databaseReference.child(Constants.HASH_TAGS).child(hashTag).child(key).updateChildren(postValues);
        }

    }*/

    public interface PostsFetched {
        void onPostsFetched(ArrayList<Post> list);
    }

    public interface RepliesFetched {
        void onRepliesFetched(ArrayList<Reply> list);
    }

    public interface CallBack {
        public void onComplete(boolean success);
    }


}
