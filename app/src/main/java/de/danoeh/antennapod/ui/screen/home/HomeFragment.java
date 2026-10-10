package de.danoeh.antennapod.ui.screen.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.ui.screen.preferences.PreferenceActivity;
import de.danoeh.antennapod.databinding.HomeFragmentBinding;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.event.FeedUpdateRunningEvent;
import de.danoeh.antennapod.model.feed.FeedItemFilter;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.ui.screen.home.sections.DownloadsSection;
import de.danoeh.antennapod.ui.screen.home.sections.EpisodesSurpriseSection;
import de.danoeh.antennapod.ui.screen.home.sections.InboxSection;
import de.danoeh.antennapod.ui.screen.home.sections.QueueSection;
import de.danoeh.antennapod.ui.screen.home.sections.SubscriptionsSection;
import de.danoeh.antennapod.ui.podcast.PodcastMetrics;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;


/**
 * Shows unread or recently published episodes
 */
public class HomeFragment extends Fragment {

    public static final String TAG = "HomeFragment";
    public static final String PREF_NAME = "PrefHomeFragment";
    public static final String PREF_HIDE_ECHO = "HideEcho";
    private static final int SECTION_GAP_DP = 28;

    private static final String KEY_UP_ARROW = "up_arrow";
    private boolean displayUpArrow;
    private HomeFragmentBinding viewBinding;
    private Disposable disposable;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        viewBinding = HomeFragmentBinding.inflate(inflater);
        viewBinding.podcastHeader.setTitle(getString(R.string.podcast_tab_home));
        viewBinding.podcastHeader.addIconButton(R.drawable.podcast_ic_gear, R.string.podcast_settings_label, false,
                v -> startActivity(new Intent(requireContext(), PreferenceActivity.class)));
        populateSectionList();
        updateWelcomeScreenVisibility();

        viewBinding.swipeRefresh.setDistanceToTriggerSync(getResources().getInteger(R.integer.swipe_refresh_distance));
        viewBinding.swipeRefresh.setOnRefreshListener(() ->
                FeedUpdateManager.getInstance().runOnceOrAsk(requireContext()));

        return viewBinding.getRoot();
    }

    private void populateSectionList() {
        viewBinding.homeContainer.removeAllViews();
        addSection(getSection(QueueSection.TAG), getSectionContainerId(QueueSection.TAG));
        addSection(getSection(InboxSection.TAG), getSectionContainerId(InboxSection.TAG));
    }

    private void addSection(Fragment section, int id) {
        if (section == null) { // Can happen when stored settings reference a section that no longer exists
            return;
        }
        FragmentContainerView containerView = new FragmentContainerView(getContext());
        containerView.setId(id);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = PodcastMetrics.dp(requireContext(), SECTION_GAP_DP);
        viewBinding.homeContainer.addView(containerView, params);
        getChildFragmentManager().beginTransaction().replace(containerView.getId(), section).commit();
    }

    private int getSectionContainerId(String sectionTag) {
        return switch (sectionTag) {
            case QueueSection.TAG -> R.id.home_section_queue;
            case InboxSection.TAG -> R.id.home_section_inbox;
            case EpisodesSurpriseSection.TAG -> R.id.home_section_surprise;
            case SubscriptionsSection.TAG -> R.id.home_section_subscriptions;
            case DownloadsSection.TAG -> R.id.home_section_downloads;
            default -> throw new IllegalArgumentException("Unknown section tag: " + sectionTag);
        };
    }

    private Fragment getSection(String tag) {
        return switch (tag) {
            case QueueSection.TAG -> new QueueSection();
            case InboxSection.TAG -> new InboxSection();
            case EpisodesSurpriseSection.TAG -> new EpisodesSurpriseSection();
            case SubscriptionsSection.TAG -> new SubscriptionsSection();
            case DownloadsSection.TAG -> new DownloadsSection();
            default -> null;
        };
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onEventMainThread(FeedUpdateRunningEvent event) {
        viewBinding.swipeRefresh.setRefreshing(event.isFeedUpdateRunning);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (disposable != null) {
            disposable.dispose();
        }
        viewBinding = null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        updateWelcomeScreenVisibility();
    }

    private void updateWelcomeScreenVisibility() {
        if (disposable != null) {
            disposable.dispose();
        }
        disposable = Observable.fromCallable(() -> DBReader.getTotalEpisodeCount(FeedItemFilter.unfiltered()))
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(numEpisodes -> {
                    boolean hasEpisodes = numEpisodes != 0;
                    viewBinding.welcomeContainer.setVisibility(hasEpisodes ? View.GONE : View.VISIBLE);
                    viewBinding.homeContainer.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
                    viewBinding.swipeRefresh.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
                    if (!hasEpisodes) {
                        viewBinding.homeScrollView.setScrollY(0);
                    }
                    viewBinding.arrowBottomIcon.setVisibility(View.VISIBLE);
                }, error -> Log.e(TAG, Log.getStackTraceString(error)));
    }

}
