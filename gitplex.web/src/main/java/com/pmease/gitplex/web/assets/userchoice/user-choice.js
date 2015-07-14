gitplex.userChoiceFormatter = {
	formatSelection: function(user) {
		return "<img class='avatar' src='" + user.avatar + "'/>" + user.name;
	},
	
	formatResult: function(user) {
		if (!user.alias) {
			return "<div class='user'>" +
					"<img class='avatar' src='" + user.avatar + "'/>" +
					"<span class='name'>"+ user.name + "</span>" +
					"</div>";
		} else {
			return "<i>&lt;&lt;" + user.alias + "&gt;&gt;</i>";
		}
	},
	
	escapeMarkup: function(m) {
		return m;
	},
	
}; 
