package com.gitplex.web.page.home;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.list.PageableListView;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;

import com.gitplex.core.GitPlex;
import com.gitplex.core.entity.Account;
import com.gitplex.core.entity.Depot;
import com.gitplex.core.entity.OrganizationMembership;
import com.gitplex.core.manager.DepotManager;
import com.gitplex.web.Constants;
import com.gitplex.web.component.avatar.Avatar;
import com.gitplex.web.page.account.overview.AccountOverviewPage;
import com.gitplex.web.page.depot.file.DepotFilePage;
import com.gitplex.web.page.layout.LayoutPage;
import com.gitplex.commons.wicket.behavior.OnTypingDoneBehavior;

import de.agilecoders.wicket.core.markup.html.bootstrap.navigation.BootstrapPagingNavigator;
import de.agilecoders.wicket.core.markup.html.bootstrap.navigation.ajax.BootstrapAjaxPagingNavigator;

@SuppressWarnings("serial")
public class DashboardPage extends LayoutPage {

	private ListView<Account> organizationsView;
	
	private WebMarkupContainer organizationsContainer; 
	
	private WebMarkupContainer noOrganizationsContainer;
	
	private PageableListView<Depot> depotsView;
	
	private BootstrapPagingNavigator depotsPageNav;
	
	private WebMarkupContainer depotsContainer; 
	
	private WebMarkupContainer noDepotsContainer;
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		add(new WebMarkupContainer("organizations") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(getLoginUser() != null);
			}

			@Override
			protected void onInitialize() {
				super.onInitialize();
				
				organizationsContainer = new WebMarkupContainer("organizations") {

					@Override
					protected void onConfigure() {
						super.onConfigure();
						setVisible(!organizationsView.getModelObject().isEmpty());
					}
					
				};
				organizationsContainer.setOutputMarkupPlaceholderTag(true);
				add(organizationsContainer);
				
				organizationsContainer.add(organizationsView = new ListView<Account>("organizations", 
						new LoadableDetachableModel<List<Account>>() {

					@Override
					protected List<Account> load() {
						List<Account> organizations = new ArrayList<>();
						
						Account loginUser = getLoginUser();
						if (loginUser != null) {
							for (OrganizationMembership membership: loginUser.getOrganizations()) {
								organizations.add(membership.getOrganization());
							}
							Collections.sort(organizations);
						}
						return organizations;
					}
					
				}) {

					@Override
					protected void populateItem(ListItem<Account> item) {
						Account organization = item.getModelObject();
						Link<Void> link = new BookmarkablePageLink<Void>("link", 
								AccountOverviewPage.class, AccountOverviewPage.paramsOf(organization));
						link.add(new Avatar("avatar", organization));
						link.add(new Label("name", organization.getDisplayName()));
						item.add(link);
					}
					
				});
				add(noOrganizationsContainer = new WebMarkupContainer("noOrganizations") {

					@Override
					protected void onConfigure() {
						super.onConfigure();
						setVisible(organizationsView.getModelObject().isEmpty());
					}
					
				});
				noOrganizationsContainer.setOutputMarkupPlaceholderTag(true);
			}
			
		});
		
		TextField<String> searchDepots;
		add(searchDepots = new TextField<String>("searchDepots", Model.of("")));
		searchDepots.add(new OnTypingDoneBehavior(100) {

			@Override
			protected void onTypingDone(AjaxRequestTarget target) {
				target.add(depotsContainer);
				target.add(depotsPageNav);
				target.add(noDepotsContainer);
			}

		});
		
		depotsContainer = new WebMarkupContainer("depots") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(!depotsView.getModelObject().isEmpty());
			}
			
		};
		depotsContainer.setOutputMarkupPlaceholderTag(true);
		add(depotsContainer);
		
		depotsContainer.add(depotsView = new PageableListView<Depot>("depots", 
				new LoadableDetachableModel<List<Depot>>() {

			@Override
			protected List<Depot> load() {
				DepotManager depotManager = GitPlex.getInstance(DepotManager.class);
				List<Depot> depots = new ArrayList<>();
				for (Depot depot: depotManager.findAllAccessible(null, getLoginUser())) {
					if (depot.matchesFQN(searchDepots.getInput())) {
						depots.add(depot);
					}
				}
				depots.sort(Depot::compareLastVisit);
				return depots;
			}
			
		}, Constants.DEFAULT_PAGE_SIZE) {

			@Override
			protected void populateItem(ListItem<Depot> item) {
				Depot depot = item.getModelObject();
				Link<Void> link = new BookmarkablePageLink<Void>("link", 
						DepotFilePage.class, DepotFilePage.paramsOf(depot)); 
				link.add(new Label("name", depot.getFQN()));
				item.add(link);
			}
			
		});

		add(depotsPageNav = new BootstrapAjaxPagingNavigator("depotsPageNav", depotsView) {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(depotsView.getPageCount() > 1);
			}
			
		});
		depotsPageNav.setOutputMarkupPlaceholderTag(true);
		
		add(noDepotsContainer = new WebMarkupContainer("noDepots") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(depotsView.getModelObject().isEmpty());
			}
			
		});
		noDepotsContainer.setOutputMarkupPlaceholderTag(true);		
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(CssHeaderItem.forReference(new DashboardResourceReference()));
	}

	@Override
	protected Component newContextHead(String componentId) {
		return new Label(componentId, "Dashboard");
	}

}