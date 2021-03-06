/*
 * jPOS Project [http://jpos.org]
 * Copyright (C) 2000-2018 jPOS Software SRL
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.jpos.qi;

import com.vaadin.data.*;
import com.vaadin.icons.VaadinIcons;
import com.vaadin.ui.*;
import com.vaadin.ui.Grid;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;
import com.vaadin.shared.ui.ContentMode;

import com.vaadin.event.ShortcutAction;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener;
import com.vaadin.shared.ui.MarginInfo;

import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.ee.BLException;
import org.jpos.ee.DB;
import org.jpos.util.FieldFactory;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;

import static org.jpos.util.QIUtils.getCaptionFromId;


public abstract class QIEntityView<T> extends VerticalLayout implements View, Configurable {
    private QI app;
    private Class<T> clazz;
    private boolean generalView;
    private String title;
    private String generalRoute;
    private String[] visibleColumns;
    private String[] visibleFields;
    private String[] readOnlyFields;
    private Grid grid;
    private RevisionsPanel revisionsPanel;
    private QIHelper helper;
    private boolean showRevisionHistoryButton;
    private Button editBtn;
    private Button removeBtn;
    private Button saveBtn;
    private Button cancelBtn;
    private Binder<T> binder;
    private Label errorLabel;
    private boolean newView;
    private Configuration cfg;
    private ViewConfig viewConfig;
    private T bean;
    private FieldFactory fieldFactory;


    public QIEntityView(Class<T> clazz, String name) {
        super();
        app = (QI) UI.getCurrent();
        this.clazz = clazz;
        this.title = "<strong>" + app.getMessage(name) + "</strong>";
        generalRoute = "/" + name;
        viewConfig = app.getView(name);
        this.visibleColumns = viewConfig.getVisibleColumns();
        this.visibleFields = viewConfig.getVisibleFields();
        this.readOnlyFields = viewConfig.getReadOnlyFields();
        setSizeFull();
        setMargin(new MarginInfo(false, false, false, false));
        setSpacing(false);
        showRevisionHistoryButton=true;
    }


    @Override
    public void enter (ViewChangeListener.ViewChangeEvent event) {
        helper = createHelper();
        helper.setConfiguration(cfg);
        if (event.getParameters() == null || event.getParameters().isEmpty()) {
            generalView = true;
            showGeneralView();
        } else {
            generalView = false;
            showSpecificView (event.getParameters());
        }
    }

    @Override
    public void attach() {
        super.attach();
    }

    public void showGeneralView () {
        try {
            Layout header = createHeader(title);
            addComponent(header);
            grid = createGrid();
            grid.setDataProvider(getHelper().getDataProvider());
            formatGrid();
            addComponent(grid);
            setExpandRatio(grid, 1);
        } catch (Exception e) {
            getApp().getLog().error(e);
            getApp().displayNotification(e.getMessage());
        }
    }

    public void showSpecificView (final String parameter) {
        Object o = null;
        String[] params = parameter.split("/|\\?");
        if (params.length > 0) {
            if ("new".equals(params[0]) && canAdd()) {
                o =  createNewEntity();
                newView = true;
            } else {
                o = getEntityByParam(params[0]);
            }
            if (parameter.contains("?")) {
                //Has query params.
                String[] queryParams= params[params.length-1].split(",");
                for (String queryParam : queryParams) {
                    String[] keyValue = queryParam.split("=");
                    if (keyValue.length > 0  && "back".equals(keyValue[0])) {
                        setGeneralRoute("/"+keyValue[1].replace(".","/"));
                    }
                }
            }
        }
        if (o == null) {
            getApp().getNavigator().navigateTo("");
            getApp().displayNotification(getApp().getMessage(
                    "errorMessage.notExists", "<strong>" + getEntityName().toUpperCase() + "</strong>: " + params[0]));
            return;
        }
        Layout header = createHeader(title + ": " + getHeaderSpecificTitle(o));
        addComponent(header);
        Panel panel = new Panel();
        panel.setSizeFull();
        addComponent(panel);
        final Layout formLayout = createForm(o, params, "new".equals(params[0]));
        panel.setContent(formLayout);
        setExpandRatio(panel, 1);
        if (!"new".equals(params[0]) && isShowRevisionHistoryButton()) {
            final Button showRevision = new Button(getApp().getMessage("showRevisionHistory"));
            showRevision.setStyleName(ValoTheme.BUTTON_LINK);
            showRevision.addClickListener(event -> {
                if (getApp().getMessage("showRevisionHistory").equals(event.getButton().getCaption())) {
                    event.getButton().setCaption(getApp().getMessage("hideRevisionHistory"));
                    loadRevisionHistory(formLayout, getEntityName().toLowerCase() + "." + params[0]);
                } else {
                    event.getButton().setCaption(getApp().getMessage("showRevisionHistory"));
                    formLayout.removeComponent(revisionsPanel);
                }
            });
            formLayout.addComponent(showRevision);
        }
    }

    protected HorizontalLayout createHeader (String title) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidth("100%");
        header.setSpacing(false);
        header.setMargin(new MarginInfo(false, true, false, true));
        Label lbl = new Label(title);
        lbl.addStyleName("h2");
        lbl.setSizeUndefined();
        lbl.setContentMode(ContentMode.HTML);
        header.addComponent(lbl);
        header.setComponentAlignment(lbl, Alignment.MIDDLE_LEFT);
        if (isGeneralView() && canAdd()) {
            Button addBtn = new Button(getApp().getMessage("add"));
            addBtn.addStyleName("borderless-colored");
            addBtn.setIcon(VaadinIcons.PLUS);
            addBtn.addClickListener(event -> navigateToNewRoute());
            header.addComponent(addBtn);
            header.setComponentAlignment(addBtn, Alignment.BOTTOM_RIGHT);
        }
        return header;
    }

    protected void navigateToNewRoute() {
        getApp().getNavigator().navigateTo(generalRoute + "/new");
    }

    public Grid createGrid() {
        Grid g = new Grid();
        g.setSizeFull();
        g.setSelectionMode(Grid.SelectionMode.SINGLE);
        g.setColumnReorderingAllowed(true);
        g.addItemClickListener(this::navigateToSpecificView);
        return g;
    }

    protected void navigateToSpecificView(Grid.ItemClick event) {
        String url = generalRoute + "/" + getHelper().getItemId(event.getItem());
        getApp().getNavigator().navigateTo(url);
    }


    public void formatGrid() {
        setGridGetters();

        //Delete not visible columns
        //Use columnId as caption
        //Set sorting for every column.
        DecimalFormat nf = new DecimalFormat();
        nf.setGroupingUsed(false);

        for (Grid.Column c : (Iterable<Grid.Column>) grid.getColumns()) {
            String columnId = c.getId();
            if (!Arrays.asList(getVisibleColumns()).contains(columnId)) {
                grid.removeColumn(columnId);
            } else {
                c.setCaption(getCaptionFromId("column." + columnId))
                        .setSortProperty(columnId)
                        .setSortable(true)
                        .setHidable(true);

                ViewConfig.FieldConfig config = viewConfig.getFields().get(c.getId());
                if (config != null) {
                    if (config.getExpandRatio() != -1)
                        c.setExpandRatio(config.getExpandRatio());
                }
                c.setStyleGenerator(obj -> {
                    Object value = c.getValueProvider().apply(obj);
                    if (value instanceof BigDecimal && !c.getId().equals("id")) {
                        return "align-right";
                    }
                    return null;
                });
            }

        }
        //fix for when a manual resize is done, the last column takes the empty space.
        grid.addColumnResizeListener(event -> {
            int lastColumnIndex = grid.getColumns().size()-1;
            ((Grid.Column)grid.getColumns().get(lastColumnIndex)).setWidth(1500);
        });
        grid.setSizeFull();
    }


    public abstract void setGridGetters();

    public Layout createForm (final Object entity, String[] params, boolean isNew) {
        VerticalLayout profileLayout = new VerticalLayout();
        profileLayout.setMargin(true);
        profileLayout.setSpacing(true);

        //Add Back Button
        if (params.length <= 1 || !"profile".equals(params[1])) {
            Button back = new Button(getApp().getMessage("back"));
            back.addStyleName("borderless-colored");
            back.setIcon(VaadinIcons.ARROW_LEFT);
            back.addClickListener(event -> app.getNavigator().navigateTo(getGeneralRoute()));
            profileLayout.addComponent(back);
            profileLayout.setComponentAlignment(back, Alignment.MIDDLE_LEFT);
        }

        binder = new Binder<T>(clazz)
        {
            @Override
            public void setReadOnly (boolean readOnly) {
                super.setReadOnly(readOnly);
                if (readOnlyFields != null) {
                    for (String fieldId : readOnlyFields) {
                        if (binder.getBinding(fieldId).isPresent()) {
                            HasValue field = binder.getBinding(fieldId).get().getField();
                            if ((field != null && !field.isEmpty()) || !field.isRequiredIndicatorVisible()) {
                                field.setReadOnly(true);
                                binder.bind(field, fieldId);
                            }
                        }

                    }
                }
            }
        };
        bean = (T) entity;
        final Layout formLayout = createLayout();
        getHelper().setOriginalEntity(bean);
        binder.readBean((T) entity);
        binder.setReadOnly(true);
        profileLayout.addComponent(formLayout);

        HorizontalLayout footer = new HorizontalLayout();
        footer.addStyleName("footer");
        footer.setMargin(new MarginInfo(true, false, false, false));
        footer.setSpacing(true);
        formLayout.addComponent(footer);

        //Add Save, Remove & Cancel Buttons
        editBtn = new Button(app.getMessage("edit"));
        removeBtn = new Button(app.getMessage("remove"));
        saveBtn = new Button(app.getMessage("save"));
        cancelBtn = new Button(app.getMessage("cancel"));

        editBtn.addClickListener(event -> editClick(event, formLayout));
        editBtn.addStyleName("icon-edit");

        saveBtn.addClickListener(event -> saveClick(event, formLayout));
        saveBtn.setVisible(false);
        saveBtn.setStyleName("icon-ok");
        saveBtn.setClickShortcut(ShortcutAction.KeyCode.ENTER);

        removeBtn.addClickListener(event -> app.addWindow(new ConfirmDialog(
                        app.getMessage("confirmTitle"),
                        app.getMessage("removeConfirmationMessage"),
                        confirm -> {
                            if (confirm) {
                                removeEntity();
                            }
                        }
        )
        ));
        removeBtn.addStyleName("icon-trash");

        cancelBtn.addClickListener(event -> {
            if (isNew) {
                app.getNavigator().navigateTo(getGeneralRoute());
            } else {
                cancelClick(event, formLayout);
            }
        });

        cancelBtn.setClickShortcut(ShortcutAction.KeyCode.ESCAPE);
        cancelBtn.setVisible(false);
        cancelBtn.addStyleName("icon-cancel");

        if (canEdit()) {
            footer.addComponent(editBtn);
            footer.addComponent(saveBtn);
            footer.addComponent(cancelBtn);
            footer.setComponentAlignment(editBtn, Alignment.MIDDLE_RIGHT);
            footer.setComponentAlignment(saveBtn, Alignment.MIDDLE_RIGHT);
            footer.setComponentAlignment(cancelBtn, Alignment.MIDDLE_RIGHT);
        }
        if (canRemove()) {
            footer.addComponent(removeBtn);
            footer.setComponentAlignment(removeBtn, Alignment.MIDDLE_RIGHT);
        }
        if (isNew) {
            editBtn.click();
        }
        errorLabel = new Label();
        errorLabel.setVisible(false);
        errorLabel.setStyleName(ValoTheme.LABEL_FAILURE);
        profileLayout.addComponent(errorLabel);
        return profileLayout;
    }
    protected void cancelClick(Button.ClickEvent event, Layout formLayout) {
        binder.readBean(bean); //this discards the changes
        binder.setReadOnly(true);
        event.getButton().setVisible(false);
        saveBtn.setVisible(false);
        editBtn.setVisible(true);
        removeBtn.setVisible(true);
        errorLabel.setVisible(false);
        errorLabel.setValue(null);
        formLayout.addStyleName(ValoTheme.FORMLAYOUT_LIGHT);
    }

    protected boolean saveClick(Button.ClickEvent event, Layout formLayout) {
        if (binder.validate().isOk()) {
            if (getEntity(bean) == null) {
                try {
                    saveEntity();
                } catch (BLException e) {
                    getApp().getLog().error(e);
                    getApp().displayNotification(e.getDetailedMessage());
                    return false;
                }
            } else {
                try {
                    updateEntity();
                } catch (BLException e) {
                    getApp().getLog().error(e);
                    getApp().displayNotification(e.getDetailedMessage());
                    return false;
                }
            }
            binder.setReadOnly(true);
            formLayout.addStyleName(ValoTheme.FORMLAYOUT_LIGHT);
            event.getButton().setVisible(false);
            cancelBtn.setVisible(false);
            editBtn.setVisible(true);
            removeBtn.setVisible(true);
            errorLabel.setValue(null);
            errorLabel.setVisible(false);
            if (revisionsPanel != null && revisionsPanel.getParent() != null) {
                Layout parent = (Layout) revisionsPanel.getParent();
                parent.removeComponent(revisionsPanel);
                loadRevisionHistory(parent, revisionsPanel.getRef());
            }
            return true;
        } else {
            BindingValidationStatus<?> result = binder.validate().getFieldValidationErrors().get(0);
            getApp().displayNotification(result.getResult().get().getErrorMessage());
            return false;
        }
    }

    protected void editClick(Button.ClickEvent event, Layout formLayout) {
        binder.setReadOnly(false);
        event.getButton().setVisible(false);
        removeBtn.setVisible(false);
        saveBtn.setVisible(true);
        cancelBtn.setVisible(true);
        formLayout.removeStyleName(ValoTheme.FORMLAYOUT_LIGHT);
    }

    protected Layout createLayout() {
        FormLayout layout = new FormLayout();
        layout.addStyleName(ValoTheme.FORMLAYOUT_LIGHT);
        layout.addStyleName("qi-form");
        layout.setMargin(new MarginInfo(false));
        addFields(layout);
        return layout;
    }

    public FieldFactory createFieldFactory () {
        return new FieldFactory(getBean(), getViewConfig(), getBinder());
    }

    protected void addFields(Layout l) {
        fieldFactory = createFieldFactory();
        for (String id : getVisibleFields()) {
            //Check if there's a custom builder
            Component field = buildAndBindCustomComponent(id);
            if (field == null) {
                //if it wasn't built yet, build it now.
                try {
                    l.addComponent(fieldFactory.buildAndBindField(id));
                } catch (NoSuchFieldException e) {
                    getApp().getLog().error(e);
                }
            } else {
                l.addComponent(field);
            }
        }
    }
    //Override on specific views to create a custom field for a certain property, or to add validators.
    // Do not forget to getValidators and add them.
    protected Component buildAndBindCustomComponent(String propertyId) {
        return null;
    }

    protected Binder.BindingBuilder formatField (String propertyId, HasValue field) {
        return getFieldFactory().formatField(propertyId, field);
    }

    protected boolean isRequired(String propertyId) {
        return getFieldFactory().isRequired(propertyId);
    }

    private void loadRevisionHistory (Layout formLayout, String ref) {
        DB db = new DB();
        db.open();
        revisionsPanel = new RevisionsPanel(ref, db);
        formLayout.addComponent(revisionsPanel);
        db.close();
    }

    public Object createNewEntity (){
        return getHelper().createNewEntity();
    }
    public abstract QIHelper createHelper ();

    public void removeEntity () throws BLException {
        if (getHelper().removeEntity()) {
            getApp().getNavigator().navigateTo(getGeneralRoute());
            getApp().displayNotification(getApp().getMessage("removed", getApp().getMessage(getEntityName()).toUpperCase()));
        }
    }

    public void saveEntity () throws BLException {
        if (getHelper().saveEntity(getBinder())) {
            app.displayNotification(app.getMessage("created", getApp().getMessage(getEntityName()).toUpperCase()));
            app.getNavigator().navigateTo(getGeneralRoute());
        }
    }

    public Object getEntityByParam (String param)  {
        return getHelper().getEntityByParam(param);
    }

    public final String getEntityName () {
        return getHelper().getEntityName();
    }

    public void updateEntity () throws BLException {
        if (getHelper().updateEntity(getBinder()))
            getApp().displayNotification(getApp().getMessage("updated", getApp().getMessage(getEntityName()).toUpperCase()));
        else
            getApp().displayNotification(getApp().getMessage("notchanged"));
    }

    public abstract Object getEntity (Object entity);
    public abstract String getHeaderSpecificTitle (Object entity);

    public boolean canEdit() {
        return false;
    }

    public boolean canAdd() {
        return false;
    }

    public boolean canRemove() {
        return false;
    }

    public QI getApp() {
        return app;
    }

    public void setApp(QI app) {
        this.app = app;
    }

    public String getGeneralRoute() {
        return generalRoute;
    }

    public void setGeneralRoute(String generalRoute) {
        this.generalRoute = generalRoute;
    }

    public boolean isGeneralView() {
        return generalView;
    }

    public void setGeneralView(boolean generalView) {
        this.generalView = generalView;
    }

    public String getTitle () {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Grid getGrid() {
        return grid;
    }

    public void setGrid(Grid grid) {
        this.grid = grid;
    }

    public String[] getVisibleColumns() {
        return visibleColumns;
    }

    public void setVisibleColumns(String[] visibleColumns) {
        this.visibleColumns = visibleColumns;
    }

    public String[] getVisibleFields() {
        return visibleFields;
    }

    public void setVisibleFields(String[] visibleFields) {
        this.visibleFields = visibleFields;
    }

    public String[] getReadOnlyFields() {
        return readOnlyFields;
    }

    public void setReadOnlyFields(String[] readOnlyFields) {
        this.readOnlyFields = readOnlyFields;
    }

    public QIHelper getHelper() {
        return helper;
    }

    public void setHelper(QIHelper helper) {
        this.helper = helper;
    }

    public Label getErrorLabel() {
        return errorLabel;
    }

    public void setErrorLabel(Label errorLabel) {
        this.errorLabel = errorLabel;
    }

    public boolean isShowRevisionHistoryButton() {
        return showRevisionHistoryButton;
    }

    public void setShowRevisionHistoryButton(boolean showRevisionHistoryButton) {
        this.showRevisionHistoryButton = showRevisionHistoryButton;
    }

    public Button getEditBtn() {
        return editBtn;
    }

    public void setEditBtn(Button editBtn) {
        this.editBtn = editBtn;
    }

    public Button getRemoveBtn() {
        return removeBtn;
    }

    public void setRemoveBtn(Button removeBtn) {
        this.removeBtn = removeBtn;
    }

    public Button getSaveBtn() {
        return saveBtn;
    }

    public void setSaveBtn(Button saveBtn) {
        this.saveBtn = saveBtn;
    }

    public Button getCancelBtn() {
        return cancelBtn;
    }

    public void setCancelBtn(Button cancelBtn) {
        this.cancelBtn = cancelBtn;
    }


    public T getInstance() {
        return bean;
    }

    public Binder<T> getBinder() {
        return this.binder;
    }

    public boolean isNewView() {
        return newView;
    }

    public void setNewView(boolean newView) {
        this.newView = newView;
    }

    public void setConfiguration (Configuration cfg) {
        this.cfg = cfg;
        String name = cfg.get("name");
        if (name != null && QI.getQI().getView(name)!= null)  {
            this.setViewConfig(QI.getQI().getView(name));
        }
    }

    public Configuration getConfiguration() {
        return cfg;
    }

    public ViewConfig getViewConfig() {
        return viewConfig;
    }

    public void setViewConfig(ViewConfig viewConfig) {
        this.viewConfig = viewConfig;
    }
    
    public FieldFactory getFieldFactory() {
        return fieldFactory;
    }

    public void setFieldFactory(FieldFactory fieldFactory) {
        this.fieldFactory = fieldFactory;
    }

    public T getBean() {
        return bean;
    }

    public void setBean(T bean) {
        this.bean = bean;
    }
}
