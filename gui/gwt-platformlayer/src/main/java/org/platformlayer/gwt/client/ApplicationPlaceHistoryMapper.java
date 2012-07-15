package org.platformlayer.gwt.client;

import org.platformlayer.gwt.client.home.HomePlace;
import org.platformlayer.gwt.client.itemlist.ItemListPlace;
import org.platformlayer.gwt.client.login.LoginPlace;
import org.platformlayer.gwt.client.projectlist.ProjectListPlace;

import com.google.gwt.place.shared.PlaceHistoryMapper;
import com.google.gwt.place.shared.WithTokenizers;

@WithTokenizers({ HomePlace.Tokenizer.class, LoginPlace.Tokenizer.class, ProjectListPlace.Tokenizer.class,
		ItemListPlace.Tokenizer.class })
public interface ApplicationPlaceHistoryMapper extends PlaceHistoryMapper {
}