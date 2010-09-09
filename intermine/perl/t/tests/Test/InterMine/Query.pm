package Test::InterMine::Query;

use strict;
use warnings;
use Carp qw/confess/;

use base ('Test::InterMine::Query::Core');
sub class {'InterMine::Query'}
sub args {my $test = shift; return ('fake.url');}
sub def_view { return ('Employee.name', 'Employee.address.address', 'Employee.department.name');}
sub xml {
q|<query constraintLogic="A and B and C" model="testmodel" name="" sortOrder="Employee.name asc" view="Employee.name Employee.address.address Employee.department.name">
   <constraint code="A" op="=" path="Employee.department.name" value="Sandwich Distribution"/>
   <constraint code="B" op="&lt;" path="Employee.age" value="18"/>
   <constraint code="C" op="IN" path="Employee.name">
     <value>Tom</value>
     <value>Dick</value>
     <value>Harry</value>
   </constraint>
   <constraint path="Employee" type="Manager"/>
   <join path="Employee.name" style="OUTER"/>
   <pathDescription description="The name of the employee" pathString="Employee.name"/>
 </query>|
}

sub empty_xml {q|<query model="testmodel" name="" sortOrder="Employee.name asc" view="Employee.name Employee.address.address Employee.department.name"/>|}

sub exp_url {return q|FAKEROOTFAKEPATH?format=tab&query=%3Cquery+constraintLogic%3D%22A+and+B+and+C%22+model%3D%22testmodel%22+name%3D%22%22+sortOrder%3D%22Employee.name+asc%22+view%3D%22Employee.name+Employee.address.address+Employee.department.name%22%3E%0A++%3Cconstraint+code%3D%22A%22+op%3D%22%3D%22+path%3D%22Employee.department.name%22+value%3D%22Sandwich+Distribution%22%2F%3E%0A++%3Cconstraint+code%3D%22B%22+op%3D%22%26lt%3B%22+path%3D%22Employee.age%22+value%3D%2218%22%2F%3E%0A++%3Cconstraint+code%3D%22C%22+op%3D%22IN%22+path%3D%22Employee.name%22%3E%0A++++%3Cvalue%3ETom%3C%2Fvalue%3E%0A++++%3Cvalue%3EDick%3C%2Fvalue%3E%0A++++%3Cvalue%3EHarry%3C%2Fvalue%3E%0A++%3C%2Fconstraint%3E%0A++%3Cconstraint+path%3D%22Employee%22+type%3D%22Manager%22%2F%3E%0A++%3Cjoin+path%3D%22Employee.name%22+style%3D%22OUTER%22%2F%3E%0A++%3CpathDescription+description%3D%22The+name+of+the+employee%22+pathString%3D%22Employee.name%22%2F%3E%0A%3C%2Fquery%3E%0A%0A|}

use Test::More;
use Test::Exception;
use Test::XML;
use Test::MockObject::Extends;
use Test::MockObject;

use InterMine::Service;
use InterMine::ResultIterator;

sub startup : Test(startup => 2) {
    my $test = shift;

    my $service = Test::MockObject->new;
    $service->fake_module(
	'InterMine::Service',
	new => sub {
	    return $service;
	},
    );
    $service->set_isa('InterMine::Service');
    $service->mock(
	model => sub {
	    return $test->model;
	},
    );

    $service->mock(
	root => sub {
	    return 'FAKEROOT';
	},
    );
    $service->mock(
	QUERY_PATH => sub {
	    return 'FAKEPATH';
	},
    );
    $test->{service} = $service;

    my $iterator = Test::MockObject::Extends->new('InterMine::ResultIterator');
    $iterator->mock(
	all_lines => sub {
	    my $self = shift;
	    return @_, @_, @_; #repeated so we get a list back
	},
    );
    $test->{iterator} = $iterator;
    $test->SUPER::startup;
}


sub setup : Test(setup) {
    my $test = shift;
    $test->SUPER::setup;
    my $obj = $test->class->new($test->args);
    $obj->add_view($test->def_view);
    $obj->add_constraint(
	path => 'Employee.department.name',
	op => '=',
	value => 'Sandwich Distribution',
	code => 'A',
    );
    $obj->add_constraint(
	path => 'Employee.age',
	op => '<',
	value => 18,
	code => 'B',
    );
    $obj->add_constraint(
	path => 'Employee.name',
	op => 'IN',
	values => [qw/Tom Dick Harry/],
	code => 'C',
    );
    $obj->add_constraint(
	path => 'Employee',
	type => 'Manager',
    );
    $obj->add_join('Employee.name');
    $obj->add_pathdescription(
	path => 'Employee.name',
	description => 'The name of the employee',
    );
    $test->{filled_obj} = $obj;
}

sub _methods : Test(2) {
    my $test = shift;
    $test->SUPER::_methods;
    my @methods = (
	qw/url results results_iterator to_xml service_root query_path/
    );
    can_ok($test->class, @methods);
}

sub _inheritance : Test(3) {
    my $test = shift;
    my $parent = 'InterMine::Query::Core';
    isa_ok($test->class, $parent, "Inherits ok -");
    my @roles = (qw/
	InterMine::Query::Roles::Runnable
	InterMine::Query::Roles::WriteOutAble
    /);
    for (@roles) {
	ok($test->class->does($_), "... and does $_");
    }
}

sub service_methods : Test(4) {
    my $test = shift;
    my $obj = $test->{filled_obj};
    my $service = $test->{service};
    $service->mock(
	get_results_iterator => sub {
	    my $self = shift;
	    return @_;
	},
    );
    is($obj->service_root, 'FAKEROOT', "Delegates root correctly");
    is($obj->query_path, 'FAKEPATH', "... and querypath likewise");
    is_deeply(
	[$obj->results_iterator],
	[
	    $test->exp_url,
	    [$test->def_view],
	    undef,
	],
	"... and results iterator likewise",
    );
    is_deeply(
	[$obj->results_iterator(with => [qw/a b c/])],
	[
	    $test->exp_url,
	    [$test->def_view],
	    ['a', 'b', 'c'],
	],
	"... and results iterator likewise with roles",
    );
}

sub to_xml : Test(3) {
    my $test = shift;
    my $obj = $test->{filled_obj};
    is_xml($obj->to_xml, $test->xml, "Serialises to xml ok");
    $obj = $test->{object};
    dies_ok(
	sub {$obj->to_xml},
	"dies trying to serialise without at least a view defined",
    );
    $obj->add_view($test->def_view);
    is_xml($obj->to_xml, $test->empty_xml, "Serialises empty queries ok");
}

sub url : Test {
    my $test = shift;
    my $obj  = $test->{filled_obj};
    is($obj->url, $test->exp_url, "Makes a good url");
}

sub results : Test(4) {
    my $test = shift;
    my $obj  = $test->{filled_obj};
    my $service = $test->{service};
    $service->mock(
	get_results_iterator => sub {
	    return $test->{iterator};
	},
    );
    is(
	$obj->results(as => 'string'),
	"string\nstring\nstring",
	"returns new-line joined string for string results",
    );
    is_deeply(
	$obj->results(as => 'arrayref'),
	['arrayref', 'arrayref', 'arrayref'],
	"returns array ref of arrayrefs for arrayref results",
    );
    is_deeply(
	$obj->results(as => 'hashref'),
	['hashref', 'hashref', 'hashref'],
	"returns array ref of hashrefs for hashref results",
    );
    is_deeply(
	$obj->results(),
	['arrayref', 'arrayref', 'arrayref'],
	"Default as per arrayref",
    );
}

1;
